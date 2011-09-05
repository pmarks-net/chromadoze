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
import java.util.Random;

import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.os.Process;

/* Crossfade notes:

When adding two streams together, the perceived amplitude stays constant
if x^2 + y^2 == 1 for amplitudes x, y.

A useful identify is: sin(x)^2 + cos(x)^2 == 1.

Thus, we can perform a constant-amplitude crossfade using:
  result = fade_out * cos(x) + fade_in * sin(x)
  for x in [0, pi/2]
  
But we also need to prevent clipping.  The maximum of sin(x) + cos(x)
occurs at the midpoint, with a result of sqrt(2), or ~1.414.

Thus, for a 16-bit output stream in the range +/-32767, we need to keep the
individual streams below 32767 / sqrt(2), or ~23170.
*/

class SampleShuffler {
    public static final int FADE_LEN = 500;
    public static final int MIN_AUDIO_BUFFER_LEN = 12288;
    public static final int SAMPLE_RATE = 44100;

    public static final float BASE_AMPLITUDE = 20000;
    public static final float CLIP_AMPLITUDE = 23000;  // 32K/sqrt(2)
    
    private ArrayList<AudioChunk> mAudioChunks = null;
    private Random mRandom = new Random();
    
    private boolean mStopThread = false;
    
    private float mGlobalVolumeFactor;
    
    private float mFadeInEnvelope[];
    private float mFadeOutEnvelope[];
    
    // Filler state.
    private int mFillCursor;
    private short mChunk0[];
    private short mChunk1[];
    
    private PlaybackThread mPlaybackThread;
    
    public SampleShuffler() {
        makeFadeEnvelopes();
        resetFillState(null);
        
        // Start playing silence until real data arrives.
        exchangeChunk(new AudioChunk(new float[MIN_AUDIO_BUFFER_LEN]));
        
        mPlaybackThread = new PlaybackThread();
        mPlaybackThread.start();
    }
    
    public void stopThread() {
        synchronized (this) {
            mStopThread = true;
        }
        try {
            mPlaybackThread.join();
        } catch (InterruptedException e) {
        }
    }
    
    // Compute the shape of a crossfade curve, on startup only.
    private void makeFadeEnvelopes() {
        mFadeInEnvelope = new float[FADE_LEN];
        mFadeOutEnvelope = new float[FADE_LEN];
        for (int i = 0; i < FADE_LEN; i++) {
            double progress = (double)i / (FADE_LEN - 1);
            mFadeInEnvelope[i] = (float)Math.sin(progress * Math.PI / 2.0);
            mFadeOutEnvelope[i] = (float)Math.cos(progress * Math.PI / 2.0);
        }
    }

    private static class StopThread extends Exception {
        private static final long serialVersionUID = 2439290876882896774L;
    }
    
    private class AudioChunk {
        private int mLength;
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
            mPcmData = new short[mLength];
            for (int i = 0; i < FADE_LEN; i++) {
                float fadeFactor = mFadeInEnvelope[i];
                mPcmData[i] = (short)(mFloatData[i] * volumeFactor * fadeFactor);
            }
            for (int i = FADE_LEN; i < mLength - FADE_LEN; i++) {
                mPcmData[i] = (short)(mFloatData[i] * volumeFactor);
            }
            for (int i = mLength - FADE_LEN; i < mLength; i++) {
                float fadeFactor = mFadeOutEnvelope[i - (mLength - FADE_LEN)];
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
        addChunkAndPurge(newChunk, notify);
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
        ArrayList<AudioChunk> oldChunks = exchangeChunk(newChunk);
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
        return mAudioChunks.get(mRandom.nextInt(mAudioChunks.size())).getPcmData();
    }
    
    private synchronized void addChunk(AudioChunk chunk) {
        mAudioChunks.add(chunk);
    }
    
    private synchronized void addChunkAndPurge(AudioChunk chunk, boolean notify) {
        mAudioChunks.clear();
        mAudioChunks.add(chunk);
        if (notify) {
            resetFillState(null);
        }
    }
    
    private synchronized ArrayList<AudioChunk> exchangeChunk(AudioChunk chunk) {
        ArrayList<AudioChunk> oldChunks = mAudioChunks;
        mAudioChunks = new ArrayList<AudioChunk>();
        mAudioChunks.add(chunk);
        return oldChunks;
    }
    
    private synchronized void fillBuffer(short[] out) throws StopThread {
        if (mStopThread) {
            throw new StopThread();
        }
        
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
                return;
            }
            
            // Fill from the crossfade between two chunks.
            if (mChunk1 == null) {
                mChunk1 = getRandomChunk();
            }
            while (mFillCursor < mChunk0.length) {
                out[outPos] = (short)(mChunk0[mFillCursor] +
                                      mChunk1[mFillCursor - firstFadeSample]);
                mFillCursor++;
                outPos++;
                if (outPos >= out.length) {
                    return;
                }
            }

            // Consumed all the fade data; switch to the next chunk.
            resetFillState(mChunk1);
        }
    }
    
    private class PlaybackThread extends Thread {

        private int mAudioBufferLen;
        
        PlaybackThread() {
            super("SampleShufflerThread");
        }
        
        public void run() {
            Process.setThreadPriority(Process.THREAD_PRIORITY_AUDIO);
            
            // Possibly increase the buffer size, if our default is too small.
            mAudioBufferLen = Math.max(
                    AudioTrack.getMinBufferSize(
                            SAMPLE_RATE,
                            AudioFormat.CHANNEL_CONFIGURATION_MONO,
                            AudioFormat.ENCODING_PCM_16BIT),
                    MIN_AUDIO_BUFFER_LEN);

            AudioTrack track = new AudioTrack(
                    AudioManager.STREAM_MUSIC,
                    SAMPLE_RATE,
                    AudioFormat.CHANNEL_CONFIGURATION_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    mAudioBufferLen,
                    AudioTrack.MODE_STREAM);
            
            track.play();
            
            try {
                short[] buf = new short[mAudioBufferLen / 2];
                while (true) {
                    fillBuffer(buf);
                    track.write(buf, 0, buf.length);
                }
            } catch (StopThread e) {
            }
            track.release();
        }
    }
}
