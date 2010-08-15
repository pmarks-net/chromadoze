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

import java.util.Random;

import android.os.Handler;
import android.os.Looper;
import android.os.Process;
import edu.emory.mathcs.jtransforms.dct.FloatDCT_1D;

public class SampleGenerator {
    private Handler mHandler;
    private Worker mWorkerThread;
    private SpectrumData mPendingSpectrum;
    private SampleShuffler mSampleShuffler;

    private int mLastDctSize = -1;
    private FloatDCT_1D mDct;
    private Random mRandom = new Random();
    
    // Chunk-making progress:
    private SpectrumData mSpectrum;
    private SampleGeneratorState mState;
    
    public SampleGenerator(SampleShuffler sampleShuffler) {
        mSampleShuffler = sampleShuffler;
        mState = new SampleGeneratorState();
        
        mWorkerThread = new Worker();
        mWorkerThread.start();
        mHandler = mWorkerThread.getHandler();
    }
    
    public void stopThread() {
        mHandler.postAtFrontOfQueue(stopLooping);
        try {
            mWorkerThread.join();
        } catch (InterruptedException e) {
        }
    }
    
    public synchronized void updateSpectrum(SpectrumData spectrum) {
        mPendingSpectrum = spectrum;
        mHandler.postAtFrontOfQueue(startNewChunks);
    }
    
    private synchronized SpectrumData popPendingSpectrum() {
        try {
            return mPendingSpectrum;
        } finally {
            mPendingSpectrum = null;
        }
    }
    
    private static class Worker extends Thread {
        private Handler mHandler;
        
        public void run() {
            Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND);
            Looper.prepare();
            synchronized (this) {
                mHandler = new Handler();
                notifyAll();
            }
            Looper.loop();
        }
        
        public synchronized Handler getHandler() {
            while (mHandler == null) {
                try {
                    wait();
                } catch (InterruptedException e) {
                }
            }
            return mHandler;
        }
    }
    
    private Runnable startNewChunks = new Runnable() {
        public void run() {
            SpectrumData spectrum = popPendingSpectrum();
            if (spectrum == null) {
                // No new spectrum available.
                return;
            }
            mSpectrum = spectrum;
            mState.reset();
            mHandler.post(makeNextChunk);
        }
    };
    
    private Runnable makeNextChunk = new Runnable() {
        public void run() {
            if (mState.done()) {
                // No chunks left to make.
                mDct = null;
                mLastDctSize = -1;
                return;
            }
            
            int dctSize = mState.getChunkSize();
            float[] dctData = doIDCT(dctSize);
            
            if (mSampleShuffler.handleChunk(dctData, mState.getStage())) {
                // Not dropped.
                mState.advance();
            }
            mHandler.post(makeNextChunk);
        }
        
    };
    
    private Runnable stopLooping = new Runnable() {
        public void run() {
            mHandler.removeCallbacks(startNewChunks);
            mHandler.removeCallbacks(makeNextChunk);
            Looper.myLooper().quit();
        }
    };
    
    private float[] doIDCT(int dctSize) {
        if (dctSize != mLastDctSize) {
            mDct = new FloatDCT_1D(dctSize);
            mLastDctSize = dctSize;
        }
        float[] dctData = new float[dctSize];
        
        mSpectrum.fill(dctData, SampleShuffler.SAMPLE_RATE);
        
        // Multiply by a block of white noise.
        for (int i = 0; i < dctSize; i += 4) {
            int rand = mRandom.nextInt();
            dctData[i] *= (byte)rand / 128f;
            dctData[i + 1] *= (byte)(rand >> 8) / 128f;
            dctData[i + 2] *= (byte)(rand >> 16) / 128f;
            dctData[i + 3] *= (byte)(rand >> 24) / 128f;
        }
        
        mDct.inverse(dctData, false);
        return dctData;
    }

}
