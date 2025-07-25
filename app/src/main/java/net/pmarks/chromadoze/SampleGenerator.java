// Copyright (C) 2010  Paul Marks  http://www.pmarks.net/
//
// This file is part of ChromaDoze.
//
// ChromaDoze is free software: you can redistribute it and/or modify
// it under the terms of the GNU General Public License as published by
// the Free Software Foundation, either version 3 of the License, or
// (at your option) any later version.
//
// ChromaDoze is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
// GNU General Public License for more details.
//
// You should have received a copy of the GNU General Public License
// along with ChromaDoze.  If not, see <http://www.gnu.org/licenses/>.

package net.pmarks.chromadoze;

import android.os.Process;
import android.os.SystemClock;

import org.jtransforms.dct.FloatDCT_1D;

class SampleGenerator {
    private final NoiseService mNoiseService;
    private final AudioParams mParams;
    private final SampleShuffler mSampleShuffler;
    private final Thread mWorkerThread;

    // Communication variables; must be synchronized.
    private boolean mStopping;
    private SpectrumData mPendingSpectrum;

    // Variables accessed from the thread only.
    private int mLastDctSize = -1;
    private FloatDCT_1D mDct;
    private final XORShiftRandom mRandom = new XORShiftRandom();  // Not thread safe.

    public SampleGenerator(NoiseService noiseService, AudioParams params,
                           SampleShuffler sampleShuffler) {
        mNoiseService = noiseService;
        mParams = params;
        mSampleShuffler = sampleShuffler;

        mWorkerThread = new Thread("SampleGeneratorThread") {
            @Override
            public void run() {
                try {
                    threadLoop();
                } catch (StopException e) {
                }
            }
        };
        mWorkerThread.start();
    }

    public void stopThread() {
        synchronized (this) {
            mStopping = true;
            notify();
        }
        try {
            mWorkerThread.join();
        } catch (InterruptedException e) {
        }
    }

    public synchronized void updateSpectrum(SpectrumData spectrum) {
        mPendingSpectrum = spectrum;
        notify();
    }

    private void threadLoop() throws StopException {
        Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND);

        // Chunk-making progress:
        final SampleGeneratorState state = new SampleGeneratorState();
        SpectrumData spectrum = null;
        long waitMs = -1;

        while (true) {
            // This does one of 3 things:
            // - Throw StopException if stopThread() was called.
            // - Check if a new spectrum is waiting.
            // - Block if there's no work to do.
            final SpectrumData newSpectrum = popPendingSpectrum(waitMs);
            if (newSpectrum != null && !newSpectrum.sameSpectrum(spectrum)) {
                spectrum = newSpectrum;
                state.reset();
                mNoiseService.updatePercentAsync(state.getPercent());
            } else if (waitMs == -1) {
                // Nothing changed.  Keep waiting.
                continue;
            }

            final long startMs = SystemClock.elapsedRealtime();

            // Generate the next chunk of sound.
            float[] dctData = doIDCT(state.getChunkSize(), spectrum);
            if (mSampleShuffler.handleChunk(dctData, state.getStage())) {
                // Not dropped.
                state.advance();
                mNoiseService.updatePercentAsync(state.getPercent());
            }

            // Avoid burning the CPU while the user is scrubbing.  For the
            // first couple large chunks, the next chunk should be ready
            // when this one is ~75% finished playing.
            final long sleepTargetMs = state.getSleepTargetMs(mParams.SAMPLE_RATE);
            final long elapsedMs = SystemClock.elapsedRealtime() - startMs;
            waitMs = sleepTargetMs - elapsedMs;
            if (waitMs < 0) waitMs = 0;
            if (waitMs > sleepTargetMs) waitMs = sleepTargetMs;

            if (state.done()) {
                // No chunks left; save RAM.
                mDct = null;
                mLastDctSize = -1;
                waitMs = -1;
            }
        }
    }

    private synchronized SpectrumData popPendingSpectrum(long waitMs)
            throws StopException {
        if (waitMs != 0 && !mStopping && mPendingSpectrum == null) {
            // Wait once.  The retry loop is in the caller.
            try {
                if (waitMs < 0) {
                    wait(/*forever*/);
                } else {
                    wait(waitMs);
                }
            } catch (InterruptedException e) {
            }
        }
        if (mStopping) {
            throw new StopException();
        }
        try {
            return mPendingSpectrum;
        } finally {
            mPendingSpectrum = null;
        }
    }

    private float[] doIDCT(int dctSize, SpectrumData spectrum) {
        if (dctSize != mLastDctSize) {
            mDct = new FloatDCT_1D(dctSize);
            mLastDctSize = dctSize;
        }
        float[] dctData = new float[dctSize];

        spectrum.fill(dctData, mParams.SAMPLE_RATE);

        // Multiply by a block of white noise.
        for (int i = 0; i < dctSize; ) {
            long rand = mRandom.nextLong();
            for (int b = 0; b < 8; b++) {
                dctData[i++] *= (byte) rand / 128f;
                rand >>= 8;
            }
        }

        mDct.inverse(dctData, false);
        return dctData;
    }

    private static class StopException extends Exception {
    }
}
