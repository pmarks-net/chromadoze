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

import android.os.Parcel;
import android.os.Parcelable;

// SpectrumData is a Phonon translated into "machine readable" form.
//
// In other words, the values here are suitable for generating noise,
// and not for storage or rendering UI elements.

public class SpectrumData implements Parcelable {
    public static final Parcelable.Creator<SpectrumData> CREATOR
            = new Parcelable.Creator<SpectrumData>() {
        @Override
        public SpectrumData createFromParcel(Parcel in) {
            return new SpectrumData(in);
        }

        @Override
        public SpectrumData[] newArray(int size) {
            return new SpectrumData[size];
        }
    };

    public static final float MIN_FREQ = 100;
    public static final float MAX_FREQ = 20000;
    public static final int BAND_COUNT = 32;

    // The frequency of the edges between each bar.
    public static final int EDGE_FREQS[] = calculateEdgeFreqs();

    private final float[] mData;
    private final float mMinVol;
    private final float mPeriod;

    private static int[] calculateEdgeFreqs() {
        int[] edgeFreqs = new int[BAND_COUNT + 1];
        float range = MAX_FREQ / MIN_FREQ;
        for (int i = 0; i <= BAND_COUNT; i++) {
            edgeFreqs[i] = (int)(MIN_FREQ * Math.pow(range, (float)i / BAND_COUNT));
        }
        return edgeFreqs;
    }

    public SpectrumData(float[] donateBars, float minVol, float period) {
        if (donateBars.length != BAND_COUNT) {
            throw new RuntimeException("Incorrect number of bands");
        }
        mData = donateBars;
        for (int i = 0; i < BAND_COUNT; i++) {
            if (mData[i] <= 0f) {
                mData[i] = 0f;
            } else {
                mData[i] = 0.001f * (float)Math.pow(1000, mData[i]);
            }
        }
        mMinVol = minVol;
        mPeriod = period;
    }

    private SpectrumData(Parcel in) {
        mData = new float[BAND_COUNT];
        in.readFloatArray(mData);
        mMinVol = in.readFloat();
        mPeriod = in.readFloat();
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeFloatArray(mData);
        dest.writeFloat(mMinVol);
        dest.writeFloat(mPeriod);
    }

    public void fill(float[] out, int sampleRate) {
        int maxFreq = sampleRate / 2;
        subFill(out, 0f, 0, EDGE_FREQS[0], maxFreq);
        for (int i = 0; i < BAND_COUNT; i++) {
            subFill(out, mData[i], EDGE_FREQS[i], EDGE_FREQS[i + 1], maxFreq);
        }
        subFill(out, 0f, EDGE_FREQS[BAND_COUNT], maxFreq, maxFreq);
    }

    private void subFill(float[] out, float setValue, int startFreq, int limitFreq, int maxFreq) {
        // This min() applies if the sample rate is below 40kHz.
        int limitIndex = Math.min(out.length, limitFreq * out.length / maxFreq);
        for (int i = startFreq * out.length / maxFreq; i < limitIndex; i++) {
            out[i] = setValue;
        }
    }

    public float getMinVol() {
        return mMinVol;
    }

    public float getPeriod() {
        return mPeriod;
    }

    public boolean sameSpectrum(SpectrumData other) {
        if (other == null) {
            return false;
        }
        for (int i = 0; i < BAND_COUNT; i++) {
            if (mData[i] != other.mData[i]) {
                return false;
            }
        }
        return true;
    }
}
