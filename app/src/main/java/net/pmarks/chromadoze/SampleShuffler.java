// Copyright (C) 2010  Paul Marks  http://www.pmarks.net/
//
// This file is part of Chroma Doze.
//
// Chroma Doze is free software: you can redistribute it and/or modify
// it under the terms of the GNU General Public License as published by
// the Free Software Foundation, either version 3 of the License, or
// (at your option) any later version.
//
// Chroma Doze is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
// GNU General Public License for more details.
//
// You should have received a copy of the GNU General Public License
// along with Chroma Doze.  If not, see <http://www.gnu.org/licenses/>.

package net.pmarks.chromadoze;

import android.media.AudioTrack;
import android.os.Process;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;

/* Crossfade notes:

When adding two streams together, the perceived amplitude stays constant
if x^2 + y^2 == 1 for amplitudes x, y.

A useful identity is: sin(x)^2 + cos(x)^2 == 1.

Thus, we can perform a constant-amplitude crossfade using:
  result = fade_out * cos(x) + fade_in * sin(x)
  for x in [0, pi/2]

But we also need to prevent clipping.  The maximum of sin(x) + cos(x)
occurs at the midpoint, with a result of sqrt(2), or ~1.414.

Thus, for a 16-bit output stream in the range +/-32767, we need to keep the
individual streams below 32767 / sqrt(2), or ~23170.
*/

class SampleShuffler {
    // These lengths are measured in samples.
    private static final int SINE_LEN = 1 << 12;
    // FADE_LEN follows the "interior", excluding 0 or 1 values.
    private static final int FADE_LEN = SINE_LEN - 1;
    private static final float BASE_AMPLITUDE = 20000;
    private static final float CLIP_AMPLITUDE = 23000;  // 32K/sqrt(2)

    private final AudioParams mParams;

    private List<AudioChunk> mAudioChunks = null;
    private final ShuffleBag mShuffleBag = new ShuffleBag();

    private float mGlobalVolumeFactor;

    // Sine wave, 4*SINE_LEN points, from [0, 2pi).
    private static final float SINE[];

    static {
        SINE = new float[4 * SINE_LEN];
        // First quarter, compute directly.
        for (int i = 0; i <= SINE_LEN; i++) {
            double progress = (double) i / SINE_LEN;
            SINE[i] = (float) Math.sin(progress * Math.PI / 2.0);
        }
        // Second quarter, flip the first horizontally.
        for (int i = SINE_LEN + 1; i < 2 * SINE_LEN; i++) {
            SINE[i] = SINE[2 * SINE_LEN - i];
        }
        // Third/Fourth quarters, flip the first two vertically.
        for (int i = 2 * SINE_LEN; i < 4 * SINE_LEN; i++) {
            SINE[i] = -SINE[i - 2 * SINE_LEN];
        }
    }

    // Filler state.
    // Note that it's essential for this to be synchronized, because
    // the shuffle thread can steal the next fillBuffer() at any time.
    private int mCursor0;
    private short mChunk0[];
    private short mChunk1[];
    private short mAlternateFuture[] = null;

    private AmpWave mAmpWave = new AmpWave(1f, 0f);

    private final PlaybackThread mPlaybackThread;

    public SampleShuffler(AudioParams params) {
        mParams = params;
        mPlaybackThread = new PlaybackThread();
    }

    public void stopThread() {
        mPlaybackThread.stopPlaying();
        try {
            mPlaybackThread.join();
        } catch (InterruptedException e) {
        }
        // Explicitly discard chunks to make life easier for the garbage
        // collector.  Comment this out to make memory leaks more obvious.
        // (Hopefully, I fixed the leak that prompted me to do this.)
        mAudioChunks = null;
    }

    public interface VolumeListener {
        public enum DuckLevel {SILENT, DUCK, NORMAL}

        public void setDuckLevel(DuckLevel d);

        public void setVolumeLevel(float v);  // Range is 0..1
    }

    public VolumeListener getVolumeListener() {
        return mPlaybackThread;
    }

    public synchronized void setAmpWave(float minVol, float period) {
        if (mAmpWave.mMinVol != minVol || mAmpWave.mPeriod != period) {
            mAmpWave = new AmpWave(minVol, period);
        }
    }

    // This class keeps track of a set of numbers, and dishes them out in
    // a random order, while maintaining a minimum distance between two
    // occurrences of the same number.
    private static class ShuffleBag {
        // Chunks that have never been played before, in arbitrary order.
        private final List<Integer> newQueue = new ArrayList<>();
        // Recent chunks sit here to avoid being played too soon.
        private final List<Integer> feederQueue = new ArrayList<>();
        // Randomly draw chunks from here.
        private List<Integer> drawPile = new ArrayList<>();
        // Chunks go here once they've been played.
        private List<Integer> discardPile = new ArrayList<>();

        private final XORShiftRandom mRandom = new XORShiftRandom();  // Not thread safe.

        public void clear() {
            newQueue.clear();
            feederQueue.clear();
            drawPile.clear();
            discardPile.clear();
        }

        public void put(int x, boolean neverPlayed) {
            // Put never-played chunks into the newQueue.
            // There's no ideal place for the old chunks, but drawPile is simplest.
            (neverPlayed ? newQueue : drawPile).add(x);
        }

        public int getNext() {
            if (!newQueue.isEmpty()) {
                return discard(pop(newQueue));
            }
            if (drawPile.isEmpty()) {
                if (!feederQueue.isEmpty()) {
                    throw new IllegalStateException();
                }
                // Everything is now in discardPile.  Move the recently-played chunks
                // to feederQueue.  Note that pop(feederQueue) will yield chunks in
                // the same order they were discarded.
                final int feederSize = discardPile.size() / 2;
                for (int i = 0; i < feederSize; i++) {
                    feederQueue.add(pop(discardPile));
                }
                // Move everything else to the drawPile.
                final List<Integer> empty = drawPile;
                drawPile = discardPile;
                discardPile = empty;
            }
            if (drawPile.isEmpty()) {
                throw new NoSuchElementException();
            }
            final int pos = mRandom.nextInt(drawPile.size());
            final int ret = drawPile.get(pos);
            if (!feederQueue.isEmpty()) {
                // Overwrite the vacant space.
                drawPile.set(pos, pop(feederQueue));
            } else {
                // Move last element to the vacant space.
                try {
                    drawPile.set(pos, pop(drawPile));
                } catch (IndexOutOfBoundsException e) {
                    // Last element *was* the vacant space.
                }
            }
            return discard(ret);
        }

        private int discard(int x) {
            discardPile.add(x);
            return x;
        }

        private static int pop(List<Integer> list) {
            return list.remove(list.size() - 1);
        }
    }

    private static class AudioChunk {
        private boolean mNeverPlayed = true;
        private float[] mFloatData;
        private short[] mPcmData;
        private float mMaxAmplitude;

        public AudioChunk(float[] floatData) {
            mFloatData = floatData;
            computeMaxAmplitude();
        }

        // Figure out the max amplitude of this chunk once.
        private void computeMaxAmplitude() {
            mMaxAmplitude = 1;  // Prevent division by zero.
            for (float sample : mFloatData) {
                if (sample < 0) sample = -sample;
                if (sample > mMaxAmplitude) mMaxAmplitude = sample;
            }
        }

        public float getMaxAmplitude() {
            return mMaxAmplitude;
        }

        public void buildPcmData(float volumeFactor) {
            final int len = mFloatData.length;
            if (len < FADE_LEN * 2) {
                throw new IllegalArgumentException("Undersized chunk: " + len);
            }
            mPcmData = new short[len];
            for (int i = 0; i < FADE_LEN; i++) {
                // Fade in using sin(x), x=(0,pi/2)
                float fadeFactor = SINE[i + 1];
                mPcmData[i] = (short) (mFloatData[i] * volumeFactor * fadeFactor);
            }
            for (int i = FADE_LEN; i < len - FADE_LEN; i++) {
                mPcmData[i] = (short) (mFloatData[i] * volumeFactor);
            }
            for (int i = len - FADE_LEN; i < len; i++) {
                int j = i - (len - FADE_LEN);
                // Fade out using cos(x), x=(0,pi/2)
                float fadeFactor = SINE[SINE_LEN + j + 1];
                mPcmData[i] = (short) (mFloatData[i] * volumeFactor * fadeFactor);
            }
        }

        public boolean neverPlayed() {
            return mNeverPlayed;
        }

        public short[] getPcmData() {
            mNeverPlayed = false;
            return mPcmData;
        }

        public void purgeFloatData() {
            mFloatData = null;
        }
    }

    public boolean handleChunk(float[] dctData, int stage) {
        SampleShuffler.AudioChunk newChunk = new AudioChunk(dctData);
        switch (stage) {
            case SampleGeneratorState.S_FIRST_SMALL:
                handleChunkPioneer(newChunk, true);
                return true;
            case SampleGeneratorState.S_OTHER_SMALL:
                handleChunkAdaptVolume(newChunk);
                return true;
            case SampleGeneratorState.S_FIRST_VOLUME:
                handleChunkPioneer(newChunk, false);
                return true;
            case SampleGeneratorState.S_OTHER_VOLUME:
                handleChunkAdaptVolume(newChunk);
                return true;
            case SampleGeneratorState.S_LAST_VOLUME:
                handleChunkFinalizeVolume(newChunk);
                return true;
            case SampleGeneratorState.S_LARGE_NOCLIP:
                return handleChunkNoClip(newChunk);
        }
        throw new RuntimeException("Invalid stage");
    }

    // Add a new chunk, deleting all the earlier ones.
    private void handleChunkPioneer(AudioChunk newChunk, boolean notify) {
        mGlobalVolumeFactor = BASE_AMPLITUDE / newChunk.getMaxAmplitude();
        exchangeChunk(withPcm(newChunk), notify);
    }

    // Add a new chunk.  If it would clip, make everything quieter.
    private void handleChunkAdaptVolume(AudioChunk newChunk) {
        if (newChunk.getMaxAmplitude() * mGlobalVolumeFactor > CLIP_AMPLITUDE) {
            changeGlobalVolume(newChunk.getMaxAmplitude(), newChunk);
        } else {
            addChunk(withPcm(newChunk));
        }
    }

    // Add a new chunk, and force a max volume that no others can cross.
    private void handleChunkFinalizeVolume(AudioChunk newChunk) {
        float maxAmplitude = newChunk.getMaxAmplitude();
        for (AudioChunk c : mAudioChunks) {
            maxAmplitude = Math.max(maxAmplitude, c.getMaxAmplitude());
        }
        if (maxAmplitude * mGlobalVolumeFactor >= BASE_AMPLITUDE) {
            changeGlobalVolume(maxAmplitude, newChunk);
        } else {
            addChunk(withPcm(newChunk));
        }

        // Delete the now-unused float data, to conserve RAM.
        for (AudioChunk c : mAudioChunks) {
            c.purgeFloatData();
        }
    }

    // Add a new chunk.  If it clips, discard it and ask for another.
    private boolean handleChunkNoClip(AudioChunk newChunk) {
        if (newChunk.getMaxAmplitude() * mGlobalVolumeFactor > CLIP_AMPLITUDE) {
            return false;
        } else {
            addChunk(withPcm(newChunk));
            return true;
        }
    }

    // Recompute all chunks with a new volume level.
    // Add a new one first, so the chunk list is never completely empty.
    private void changeGlobalVolume(float maxAmplitude, AudioChunk newChunk) {
        mGlobalVolumeFactor = BASE_AMPLITUDE / maxAmplitude;
        List<AudioChunk> oldChunks = exchangeChunk(withPcm(newChunk), false);
        List<AudioChunk> playedChunks = new ArrayList<>();

        // First, process the never-played chunks.
        for (AudioChunk c : oldChunks) {
            if (c.neverPlayed()) {
                addChunk(withPcm(c));
            } else {
                playedChunks.add(c);
            }
        }
        // Process the leftovers.
        for (AudioChunk c : playedChunks) {
            addChunk(withPcm(c));
        }
    }

    private AudioChunk withPcm(AudioChunk chunk) {
        chunk.buildPcmData(mGlobalVolumeFactor);
        return chunk;
    }

    private synchronized void resetFillState(short chunk0[]) {
        // mCursor0 begins at the first non-faded sample, not at 0.
        mCursor0 = FADE_LEN;
        mChunk0 = chunk0;
        mChunk1 = null;
    }

    private synchronized short[] getRandomChunk() {
        return mAudioChunks.get(mShuffleBag.getNext()).getPcmData();
    }

    private synchronized void addChunk(AudioChunk chunk) {
        final int pos = mAudioChunks.size();
        mAudioChunks.add(chunk);
        mShuffleBag.put(pos, chunk.neverPlayed());
    }

    private synchronized List<AudioChunk> exchangeChunk(AudioChunk chunk, boolean notify) {
        if (notify) {
            if (mAudioChunks != null && mAlternateFuture == null) {
                // Grab the chunk of data that would've been played if it
                // weren't for this interruption.  Later, we'll cross-fade it
                // with the new data to avoid pops.
                final short[] peek = new short[FADE_LEN * AudioParams.SHORTS_PER_SAMPLE];
                fillBuffer(peek);
                mAlternateFuture = peek;
            }
            resetFillState(null);
        }
        List<AudioChunk> oldChunks = mAudioChunks;
        mAudioChunks = new ArrayList<>();
        mAudioChunks.add(chunk);
        mShuffleBag.clear();
        mShuffleBag.put(0, chunk.neverPlayed());

        // Begin playback when the first chunk arrives.
        // The fade-in effect makes mAlternateFuture unnecessary.
        if (oldChunks == null) {
            mPlaybackThread.start();
        }

        return oldChunks;
    }

    // Requires: out has room for at least FADE_LEN samples.
    // Returns: the current mAmpWave.
    //
    private synchronized AmpWave fillBuffer(short[] out) {
        if (mChunk0 == null) {
            // This should only happen after a reset.
            mChunk0 = getRandomChunk();
        }

        int outPos = 0;
        outerLoop:
        while (true) {
            // Get the index within mChunk0 where the fade-out begins.
            final int firstFadeSample = mChunk0.length - FADE_LEN;

            // For cheap stereo, just play the same chunk backwards.
            int reverseCursor0 = (mChunk0.length - 1) - mCursor0;

            // Fill from the non-faded middle of the first chunk.
            while (mCursor0 < firstFadeSample) {
                out[outPos++] = mChunk0[mCursor0++];
                out[outPos++] = mChunk0[reverseCursor0--];
                if (outPos >= out.length) {
                    break outerLoop;
                }
            }

            // Fill from the crossfade between two chunks.
            if (mChunk1 == null) {
                mChunk1 = getRandomChunk();
            }
            int cursor1 = mCursor0 - firstFadeSample;
            int reverseCursor1 = (mChunk1.length - 1) - cursor1;
            while (mCursor0 < mChunk0.length) {
                out[outPos++] = (short) (mChunk0[mCursor0++] + mChunk1[cursor1++]);
                out[outPos++] = (short) (mChunk0[reverseCursor0--] + mChunk1[reverseCursor1--]);
                if (outPos >= out.length) {
                    break outerLoop;
                }
            }

            // Make sure we've consumed all the fade data.
            if (cursor1 != FADE_LEN) {
                throw new IllegalStateException("Out of sync");
            }

            // Switch to the next chunk.
            resetFillState(mChunk1);
        }

        if (mAlternateFuture != null) {
            // This means that the spectrum was abruptly changed.  Crossfade
            // from old to new, to avoid pops.  This is more CPU-intensive
            // than fading between two chunks, because the envelopes aren't
            // precomputed.  Also, this might result in clipping if the inputs
            // happen to be in the middle of a crossfade already.
            outPos = 0;
            for (int i = 1; i <= FADE_LEN; i++) {
                for (int chan = 0; chan < 2; chan++) {
                    float sample = (mAlternateFuture[outPos] * SINE[SINE_LEN + i] +
                                    out[outPos] * SINE[i]);
                    if (sample > 32767f) sample = 32767f;
                    if (sample < -32767f) sample = -32767f;
                    out[outPos++] = (short) sample;
                }
            }
            mAlternateFuture = null;
        }

        return mAmpWave;
    }

    private class AmpWave {
        // This constant defines how many virtual points map to one period
        // of the amplitude wave.  Must be a power of 2.
        public static final int SINE_PERIOD = 1 << 30;
        public static final int SINE_STRETCH = SINE_PERIOD / (4 * SINE_LEN);

        // Quietest point on the sine wave (3π/2)
        public static final int QUIET_POS = (int) (SINE_PERIOD * .75);
        // Loudest point on the sine wave (π/2)
        public static final int LOUD_POS = (int) (SINE_PERIOD * .25);

        // The minimum amplitude, from [0,1]
        public final float mMinVol;
        // The wave period, in seconds.
        public final float mPeriod;

        // Same length as mSine, but shifted/stretched according to mMinAmp.
        // We want to do the multiply using integer math, so [0.0, 1.0] is
        // stored as [0, 32767].
        private final short mTweakedSine[];

        private int mPos = QUIET_POS;
        private final int mSpeed;

        public AmpWave(float minVol, float period /* seconds */) {
            if (minVol > .999f || period < .001f) {
                mTweakedSine = null;
                mSpeed = 0;
            } else {
                // Make sure the numbers stay reasonable.
                if (minVol < 0f) minVol = 0f;
                if (period > 300f) period = 300f;

                // Make a sine wave oscillate from minVol to 100%.
                mTweakedSine = new short[4 * SINE_LEN];
                float scale = (1f - minVol) / 2f;
                for (int i = 0; i < mTweakedSine.length; i++) {
                    mTweakedSine[i] = (short) ((SINE[i] * scale + 1f - scale) * 32767f);
                }

                // When period == 1 sec, SAMPLE_RATE iterations should cover
                // SINE_PERIOD virtual points.
                mSpeed = (int) (SINE_PERIOD / (period * mParams.SAMPLE_RATE));
            }

            mMinVol = minVol;
            mPeriod = period;
        }

        // It's only safe to call this from the playback thread.
        public void copyOldPosition(AmpWave old) {
            if (old != null && old != this) {
                mPos = old.mPos;
            }
        }

        // Apply the amplitude wave to this audio buffer.
        // It's only safe to call this from the playback thread.
        // Returns true if stopAtLoud reached its target.
        public boolean mutateBuffer(short buf[], boolean stopAtLoud) {
            if (mTweakedSine == null) {
                return false;
            }
            int outPos = 0;
            while (outPos < buf.length) {
                if (stopAtLoud && LOUD_POS <= mPos && mPos < QUIET_POS) {
                    return true;  // Reached 100% volume.
                }
                // Multiply by [0, 1) using integer math.
                final short mult = mTweakedSine[mPos / SINE_STRETCH];
                mPos = (mPos + mSpeed) & (SINE_PERIOD - 1);
                for (int chan = 0; chan < AudioParams.SHORTS_PER_SAMPLE; chan++) {
                    buf[outPos] = (short) ((buf[outPos] * mult) >> 15);
                    outPos++;
                }
            }
            return false;
        }
    }

    private class PlaybackThread extends Thread implements VolumeListener {

        PlaybackThread() {
            super("SampleShufflerThread");
        }

        private boolean mPreventStart = false;
        private AudioTrack mTrack;
        private DuckLevel mDuckLevel = DuckLevel.NORMAL;
        private float mVolumeLevel = 1f;

        private synchronized boolean startPlaying() {
            if (mPreventStart || mTrack != null) {
                return false;
            }
            // I occasionally receive this crash report:
            // "java.lang.IllegalStateException: play() called on uninitialized AudioTrack."
            // Perhaps it just needs a retry loop?  I have no idea if this helps at all.
            for (int i = 1; ; i++) {
                mTrack = mParams.makeAudioTrack();
                setVolumeInternal();
                try {
                    mTrack.play();
                    return true;
                } catch (IllegalStateException e) {
                    if (i >= 3) throw e;
                    Log.w("PlaybackThread", "Failed to play(); retrying:", e);
                    System.gc();
                }
            }
        }

        public synchronized void stopPlaying() {
            if (mTrack == null) {
                mPreventStart = true;
            } else {
                mTrack.stop();
            }
        }

        // Manage "Audio Focus" by changing the volume level.
        public synchronized void setDuckLevel(DuckLevel d) {
            mDuckLevel = d;
            if (mTrack != null) {
                setVolumeInternal();
            }
        }

        public synchronized void setVolumeLevel(float v) {
            if (v < 0f || v > 1f) {
                throw new IllegalArgumentException("Invalid volume: " + v);
            }
            mVolumeLevel = v;
            if (mTrack != null) {
                setVolumeInternal();
            }
        }

        private void setVolumeInternal() {
            float v;
            switch (mDuckLevel) {
                case SILENT:
                    v = 0f;
                    break;
                case DUCK:
                    v = mVolumeLevel * 0.1f;
                    break;
                case NORMAL:
                    v = mVolumeLevel;
                    break;
                default:
                    throw new IllegalArgumentException("Invalid DuckLevel: " + mDuckLevel);
            }
            mTrack.setStereoVolume(v, v);
        }

        @Override
        public void run() {
            Process.setThreadPriority(Process.THREAD_PRIORITY_AUDIO);

            if (!startPlaying()) {
                return;
            }

            // Apply a fade-in effect on startup (half-period = 1sec)
            AmpWave fadeIn = new AmpWave(0, 2);

            // Aim to write half of the AudioTrack's buffer per iteration,
            // but FADE_LEN is the bare minimum to avoid errors.
            final short[] buf = new short[Math.max(mParams.BUF_SAMPLES / 2, FADE_LEN) *
                    AudioParams.SHORTS_PER_SAMPLE];
            AmpWave oldAmpWave = null;
            int result;
            do {
                AmpWave newAmpWave = fillBuffer(buf);
                newAmpWave.copyOldPosition(oldAmpWave);
                newAmpWave.mutateBuffer(buf, false);
                oldAmpWave = newAmpWave;
                if (fadeIn != null && fadeIn.mutateBuffer(buf, true)) {
                    fadeIn = null;
                }
                // AudioTrack will write everything, unless it's been stopped.
                result = mTrack.write(buf, 0, buf.length);
            } while (result == buf.length);

            if (result < 0) {
                Log.w("PlaybackThread", "write() failed: " + result);
            }

            mTrack.release();
        }
    }
}
