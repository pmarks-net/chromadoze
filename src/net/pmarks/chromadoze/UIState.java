// Copyright (C) 2011  Paul Marks  http://www.pmarks.net/
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

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

public class UIState {
    public static final int BAND_COUNT = SpectrumData.BAND_COUNT;

    private boolean mBarsChanged = false;
    private boolean mSendEnabled = false;
    private Context mContext;

    // The current value of each bar, [0.0, 1.0]
    private float mBars[] = new float[BAND_COUNT];

    private int mMinVol = 100;
    private int mPeriod = 50;

    public UIState(Context context) {
        mContext = context;
    }

    public void saveState(SharedPreferences.Editor pref) {
        for (int i = 0; i < BAND_COUNT; i++) {
            pref.putFloat("barHeight" + i, mBars[i]);
        }
        pref.putInt("minVol", mMinVol);
        pref.putInt("period", mPeriod);
    }

    public void loadState(SharedPreferences pref) {
        for (int i = 0; i < BAND_COUNT; i++) {
            mBars[i] = pref.getFloat("barHeight" + i, .5f);
        }
        mMinVol = pref.getInt("minVol", 100);
        mPeriod = pref.getInt("period", 50);
    }

    public void startSending() {
        mSendEnabled = true;
        sendToService();
    }

    public void stopSending() {
        mSendEnabled = false;
        Intent intent = new Intent(mContext, NoiseService.class);
        mContext.stopService(intent);
    }

    public void setSendEnabled(boolean set) {
        mSendEnabled = set;
    }

    // band: The index number of the bar.
    // value: Between 0 and 1.
    public void setBar(int band, float value) {
        if (mBars[band] != value) {
            mBars[band] = value;
            mBarsChanged = true;
        }
    }

    public float getBar(int band) {
        return mBars[band];
    }

    // Returns true if any change occurred.
    public boolean commitBars() {
        if (mBarsChanged) {
            sendToService();
            mBarsChanged = false;
            return true;
        }
        return false;
    }

    // Return true if all equalizer bars are set to zero.
    public boolean isSilent() {
        for (int i = 0; i < BAND_COUNT; i++) {
            if (mBars[i] > 0) {
                return false;
            }
        }
        return true;
    }

    // Range: [0, 100]
    public void setMinVol(int minVol) {
        if (minVol != mMinVol) {
            mMinVol = minVol;
            sendToService();
        }
    }

    public int getMinVol() {
        return mMinVol;
    }

    public String getMinVolText() {
        return mMinVol + "%";
    }

    // Range: [0, 100]
    public void setPeriod(int period) {
        if (period != mPeriod) {
            mPeriod = period;
            sendToService();
        }
    }

    public int getPeriod() {
        return mPeriod;
    }

    public String getPeriodText() {
        float s = getPeriodSeconds();
        if (s >= 1f) {
            return String.format("%.1f sec", s);
        } else {
            return String.format("%d ms", (int)(s * 1000));
        }
    }

    private float getPeriodSeconds() {
        // Map [0, 100] to a logarithmic scale.
        return 2f * (float)Math.pow(30, (mPeriod - 50) / 50f);
    }

    private void sendToService() {
        if (!mSendEnabled) {
            return;
        }
        Intent intent = new Intent(mContext, NoiseService.class);
        intent.putExtra("spectrum", new SpectrumData(
                mBars, mMinVol / 100f, getPeriodSeconds()));
        mContext.startService(intent);
    }

}
