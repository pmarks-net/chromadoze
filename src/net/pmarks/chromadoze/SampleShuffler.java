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

import java.util.ArrayList;

import android.media.AudioTrack;
import android.os.Process;
import android.util.Log;

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
    public static final int SINE_LEN = 1 << 9;
    public static final int FADE_LEN = SINE_LEN + 1;
    public static final float BASE_AMPLITUDE = 20000;
    public static final float CLIP_AMPLITUDE = 23000;  // 32K/sqrt(2)

    private final AudioParams mParams;

    private ArrayList<AudioChunk> mAudioChunks = null;
    private final XORShiftRandom mRandom = new XORShiftRandom();  // Not thread safe.
    private int mLastRandomChunk = -1;

    private float mGlobalVolumeFactor;

    // Sine wave, 4*SINE_LEN points, from [0, 2pi).
    private final float mSine[];

    // Filler state.
    private int mFillCursor;
    private short mChunk0[];
    private short mChunk1[];
    private short mAlternateFuture[] = null;

    private AmpWave mAmpWave = new AmpWave(1f, 0f);

    private final PlaybackThread mPlaybackThread;

    public SampleShuffler(AudioParams params) {
        mParams = params;
        mSine = makeSineCurve();

        // Start playing silence until real data arrives.
        exchangeChunk(new AudioChunk(new float[FADE_LEN * 2]), true);

        mPlaybackThread = new PlaybackThread();
        mPlaybackThread.start();
    }

    public void stopThread() {
        mPlaybackThread.stopPlaying();
        try {
            mPlaybackThread.join();
        } catch (InterruptedException e) {
        }
    }

    public synchronized void setAmpWave(float minVol, float period) {
        if (mAmpWave.mMinVol != minVol || mAmpWave.mPeriod != period) {
            mAmpWave = new AmpWave(minVol, period);
        }
    }

    // Generate one period of a sine wave.
    private static float[] makeSineCurve() {
        float out[] = new float[4*SINE_LEN];
        // First quarter, compute directly.
        for (int i = 0; i <= SINE_LEN; i++) {
            double progress = (double)i / SINE_LEN;
            out[i] = (float)Math.sin(progress * Math.PI / 2.0);
        }
        // Second quarter, flip the first horizontally.
        for (int i = SINE_LEN + 1; i < 2*SINE_LEN; i++) {
            out[i] = out[2*SINE_LEN - i];
        }
        // Third/Fourth quarters, flip the first two vertically.
        for (int i = 2*SINE_LEN; i < 4*SINE_LEN; i++) {
            out[i] = -out[i - 2*SINE_LEN];
        }
        return out;
    }

    private class AudioChunk {
        private final int mLength;
        private float[] mFloatData;
        private short[] mPcmData;
        private float mMaxAmplitude;

        public AudioChunk(float[] floatData) {
            mFloatData = floatData;
            mLength = mFloatData.length;
            computeMaxAmplitude();
            buildPcmData(BASE_AMPLITUDE / mMaxAmplitude);
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
            if (mLength < FADE_LEN * 2) {
                throw new IllegalArgumentException("Undersized chunk: " + mLength);
            }
            mPcmData = new short[mLength];
            for (int i = 0; i < FADE_LEN; i++) {
                // Fade in using sin(x), x=[0,pi/2]
                float fadeFactor = mSine[i];
                mPcmData[i] = (short)(mFloatData[i] * volumeFactor * fadeFactor);
            }
            for (int i = FADE_LEN; i < mLength - FADE_LEN; i++) {
                mPcmData[i] = (short)(mFloatData[i] * volumeFactor);
            }
            for (int i = mLength - FADE_LEN; i < mLength; i++) {
                int j = i - (mLength - FADE_LEN);
                // Fade out using cos(x), x=[0,pi/2]
                float fadeFactor = mSine[SINE_LEN + j];
                mPcmData[i] = (short)(mFloatData[i] * volumeFactor * fadeFactor);
            }
        }

        public short[] getPcmData() {
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
        newChunk.buildPcmData(mGlobalVolumeFactor);
        exchangeChunk(newChunk, notify);
    }

    // Add a new chunk.  If it would clip, make everything quieter.
    private void handleChunkAdaptVolume(AudioChunk newChunk) {
        if (newChunk.getMaxAmplitude() * mGlobalVolumeFactor > CLIP_AMPLITUDE) {
            changeGlobalVolume(newChunk.getMaxAmplitude(), newChunk);
        } else {
            newChunk.buildPcmData(mGlobalVolumeFactor);
            addChunk(newChunk);
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
            newChunk.buildPcmData(mGlobalVolumeFactor);
            addChunk(newChunk);
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
            newChunk.buildPcmData(mGlobalVolumeFactor);
            addChunk(newChunk);
            return true;
        }
    }

    // Recompute all chunks with a new volume level.
    // Add a new one first, so the chunk list is never completely empty.
    private void changeGlobalVolume(float maxAmplitude, AudioChunk newChunk) {
        mGlobalVolumeFactor = BASE_AMPLITUDE / maxAmplitude;
        newChunk.buildPcmData(mGlobalVolumeFactor);
        ArrayList<AudioChunk> oldChunks = exchangeChunk(newChunk, false);
        for (AudioChunk c : oldChunks) {
            c.buildPcmData(mGlobalVolumeFactor);
            addChunk(c);
        }
    }

    private synchronized void resetFillState(short chunk0[]) {
        // mFillCursor begins at the first non-faded sample, not at 0.
        mFillCursor = FADE_LEN;
        mChunk0 = chunk0;
        mChunk1 = null;
    }

    private synchronized short[] getRandomChunk() {
        int size = mAudioChunks.size();
        int pick;
        // When possible, avoid picking the same chunk twice in a row.
        if (mLastRandomChunk >= 0 && size > 1) {
            pick = mRandom.nextInt(size - 1);
            if (pick >= mLastRandomChunk) ++pick;
        } else {
            pick = mRandom.nextInt(size);
        }
        mLastRandomChunk = pick;
        return mAudioChunks.get(pick).getPcmData();
    }

    private synchronized void addChunk(AudioChunk chunk) {
        mAudioChunks.add(chunk);
    }

    private synchronized ArrayList<AudioChunk> exchangeChunk(AudioChunk chunk, boolean notify) {
        if (notify) {
            if (mAudioChunks != null && mAlternateFuture == null) {
                // Grab the chunk of data that would've been played if it
                // weren't for this interruption.  Later, we'll cross-fade it
                // with the new data to avoid pops.
                short[] peek = new short[FADE_LEN];
                this.fillBuffer(peek);
                mAlternateFuture = peek;
            }
            resetFillState(null);
        }
        ArrayList<AudioChunk> oldChunks = mAudioChunks;
        mAudioChunks = new ArrayList<AudioChunk>();
        mAudioChunks.add(chunk);
        mLastRandomChunk = -1;
        return oldChunks;
    }

    // Returns: the current mAmpWave.
    private synchronized AmpWave fillBuffer(short[] out) {
        if (mChunk0 == null) {
            // This should only happen after a reset.
            mChunk0 = getRandomChunk();
        }

        int outPos = 0;
        while (true) {
            // Get the index within mChunk0 where the fade-out begins.
            final int firstFadeSample = mChunk0.length - FADE_LEN;

            // Fill from the non-faded part of the first chunk.
            if (mFillCursor < firstFadeSample) {
                final int toWrite = Math.min(firstFadeSample - mFillCursor, out.length - outPos);
                System.arraycopy(mChunk0, mFillCursor, out, outPos, toWrite);
                mFillCursor += toWrite;
                outPos += toWrite;
            }

            if (outPos >= out.length) {
                break;
            }

            // Fill from the crossfade between two chunks.
            if (mChunk1 == null) {
                mChunk1 = getRandomChunk();
            }
            while (mFillCursor < mChunk0.length && outPos < out.length) {
                out[outPos] = (short)(mChunk0[mFillCursor] +
                        mChunk1[mFillCursor - firstFadeSample]);
                mFillCursor++;
                outPos++;
            }

            if (outPos >= out.length) {
                break;
            }

            // Consumed all the fade data; switch to the next chunk.
            resetFillState(mChunk1);
        }

        if (mAlternateFuture != null) {
            // This means that the spectrum was abruptly changed.  Crossfade
            // from old to new, to avoid pops.  This is more CPU-intensive
            // than fading between two chunks, because the envelopes aren't
            // precomputed.  Also, this might result in clipping if the inputs
            // happen to be in the middle of a crossfade already.
            for (int i = 0; i < FADE_LEN; i++) {
                float sample = mAlternateFuture[i] * mSine[SINE_LEN + i] + out[i] * mSine[i];
                if (sample > 32767f) sample = 32767f;
                if (sample < -32767f) sample = -32767f;
                out[i] = (short)sample;
            }
            mAlternateFuture = null;
        }

        return mAmpWave;
    }

    private class AmpWave {
        // This constant defines how many virtual points map to one period
        // of the amplitude wave.  Must be a power of 2.
        public static final int SINE_PERIOD = 1 << 30;
        public static final int SINE_STRETCH = SINE_PERIOD / (4*SINE_LEN);

        // The minimum amplitude, from [0,1]
        public final float mMinVol;
        // The wave period, in seconds.
        public final float mPeriod;

        // Same length as mSine, but shifted/stretched according to mMinAmp.
        // We want to do the multiply using integer math, so [0.0, 1.0] is
        // stored as [0, 32767].
        private short mTweakedSine[];

        private int mPos = (int)(SINE_PERIOD * .75);  // Quietest point.
        private int mSpeed;

        public AmpWave(float minVol, float period) {
            if (minVol > .999f || period < .001f) {
                mAmpWave = null;
                mSpeed = 0;
            } else {
                // Make sure the numbers stay reasonable.
                if (minVol < 0f) minVol = 0f;
                if (period > 300f) period = 300f;

                // Make a sine wave oscillate from minVol to 100%.
                mTweakedSine = new short[4*SINE_LEN];
                float scale = (1f - minVol) / 2f;
                for (int i = 0; i < mTweakedSine.length; i++) {
                    mTweakedSine[i] = (short)((mSine[i] * scale + 1f - scale) * 32767f);
                }

                // When period == 1 sec, SAMPLE_RATE iterations should cover
                // SINE_PERIOD virtual points.
                mSpeed = (int)(SINE_PERIOD / (period * mParams.SAMPLE_RATE));
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
        public void mutateBuffer(short buf[]) {
            if (mTweakedSine == null) {
                return;
            }
            for (int i = 0; i < buf.length; i++) {
                // Multiply by [0, 1) using integer math.
                buf[i] = (short)((buf[i] * mTweakedSine[mPos / SINE_STRETCH]) >> 15);
                mPos = (mPos + mSpeed) & (SINE_PERIOD - 1);
            }
        }
    }

    private class PlaybackThread extends Thread {

        PlaybackThread() {
            super("SampleShufflerThread");
        }

        private boolean mPreventStart = false;
        private AudioTrack mTrack;

        private synchronized boolean startPlaying() {
            if (mPreventStart || mTrack != null) {
                return false;
            }
            mTrack = mParams.makeAudioTrack();
            mTrack.play();
            return true;
        }

        public synchronized void stopPlaying() {
            if (mTrack == null) {
                mPreventStart = true;
            } else {
                mTrack.stop();
            }
        }

        @Override
        public void run() {
            Process.setThreadPriority(Process.THREAD_PRIORITY_AUDIO);

            if (!startPlaying()) {
                return;
            }

            // Aim to write half of the AudioTrack's buffer per iteration,
            // but FADE_LEN is the bare minimum to avoid errors.
            short[] buf = new short[Math.max(mParams.BUF_SHORTS / 2, FADE_LEN)];
            AmpWave oldAmpWave = null;
            int result;
            do {
                AmpWave newAmpWave = fillBuffer(buf);
                newAmpWave.copyOldPosition(oldAmpWave);
                newAmpWave.mutateBuffer(buf);
                oldAmpWave = newAmpWave;
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
