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

import android.os.Handler;
import android.os.Looper;
import android.os.Process;
import edu.emory.mathcs.jtransforms.dct.FloatDCT_1D;

public class SampleGenerator {
    private final Handler mHandler;
    private final Worker mWorkerThread;
    private SpectrumData mPendingSpectrum;
    private final NoiseService mNoiseService;
    private final AudioParams mParams;
    private final SampleShuffler mSampleShuffler;

    private int mLastDctSize = -1;
    private FloatDCT_1D mDct;
    private final XORShiftRandom mRandom = new XORShiftRandom();  // Not thread safe.

    // Chunk-making progress:
    private SpectrumData mSpectrum;
    private final SampleGeneratorState mState;

    // 'random' will only be used from one thread.
    public SampleGenerator(NoiseService noiseService, AudioParams params,
            SampleShuffler sampleShuffler) {
        mNoiseService = noiseService;
        mParams = params;
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

        Worker() {
            super("SampleGeneratorThread");
        }

        @Override
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

    private final Runnable startNewChunks = new Runnable() {
        public void run() {
            SpectrumData spectrum = popPendingSpectrum();
            if (spectrum == null || spectrum.sameSpectrum(mSpectrum)) {
                // No new spectrum available.
                return;
            }
            mState.reset();
            mNoiseService.updatePercentAsync(mState.getPercent());
            mHandler.post(makeNextChunk);
            mSpectrum = spectrum;
        }
    };

    private final Runnable makeNextChunk = new Runnable() {
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
                mNoiseService.updatePercentAsync(mState.getPercent());
            }
            mHandler.post(makeNextChunk);
        }

    };

    private final Runnable stopLooping = new Runnable() {
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

        mSpectrum.fill(dctData, mParams.SAMPLE_RATE);

        // Multiply by a block of white noise.
        for (int i = 0; i < dctSize;) {
            long rand = mRandom.nextLong();
            for (int b = 0; b < 8; b++) {
                dctData[i++] *= (byte)rand / 128f;
                rand >>= 8;
            }
        }

        mDct.inverse(dctData, false);
        return dctData;
    }

}
