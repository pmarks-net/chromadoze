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

import java.util.ArrayList;
import java.util.Locale;

import junit.framework.Assert;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

public class UIState {
    public static final int BAND_COUNT = SpectrumData.BAND_COUNT;

    private boolean mBarsChanged = false;
    private final Context mContext;

    // The current value of each bar, [0.0, 1.0]
    private final float mBars[] = new float[BAND_COUNT];

    private int mMinVol = 100;
    private int mPeriod = 18;  // Maps to 1 second
    private boolean mLocked = false;
    private boolean mLockBusy = false;
    private final ArrayList<LockListener> mLockListeners = new ArrayList<LockListener>();

    public UIState(Context context) {
        mContext = context;
    }

    public void saveState(SharedPreferences.Editor pref) {
        for (int i = 0; i < BAND_COUNT; i++) {
            pref.putFloat("barHeight" + i, mBars[i]);
        }
        pref.putInt("minVol", mMinVol);
        pref.putInt("period", mPeriod);
        pref.putBoolean("locked", mLocked);
    }

    public void loadState(SharedPreferences pref) {
        for (int i = 0; i < BAND_COUNT; i++) {
            mBars[i] = pref.getFloat("barHeight" + i, .5f);
        }
        mMinVol = pref.getInt("minVol", 100);
        mPeriod = pref.getInt("period", 18);
        mLocked = pref.getBoolean("locked", false);
    }

    public void addLockListener(LockListener l) {
        mLockListeners.add(l);
    }

    public void removeLockListener(LockListener l) {
        if (!mLockListeners.remove(l)) {
            throw new IllegalStateException();
        }
    }

    private void notifyLockListeners(LockListener.LockEvent e) {
        for (LockListener l : mLockListeners) {
            l.onLockStateChange(e);
        }
    }

    public void startSending() {
        sendToService();
    }

    public void stopSending() {
        Intent intent = new Intent(mContext, NoiseService.class);
        mContext.stopService(intent);
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
            return String.format(Locale.getDefault(), "%.2g sec", s);
        } else {
            return String.format(Locale.getDefault(), "%d ms", Math.round(s * 1000));
        }
    }

    public void toggleLocked() {
        mLocked = !mLocked;
        if (!mLocked) {
            mLockBusy = false;
        }
        notifyLockListeners(LockListener.LockEvent.TOGGLE);
    }

    public boolean getLocked() {
        return mLocked;
    }

    public void setLockBusy(boolean busy) {
        Assert.assertTrue(mLocked);
        if (mLockBusy != busy) {
            mLockBusy = busy;
            notifyLockListeners(LockListener.LockEvent.BUSY);
        }
    }

    public boolean getLockBusy() {
        return mLockBusy;
    }

    private float getPeriodSeconds() {
        // This is a somewhat human-friendly mapping from
        // scroll position to seconds.
        if (mPeriod < 9) {
            // 10ms, 20ms, ..., 90ms
            return (mPeriod + 1) * .010f;
        } else if (mPeriod < 18) {
            // 100ms, 200ms, ..., 900ms
            return (mPeriod - 9 + 1) * .100f;
        } else if (mPeriod < 36) {
            // 1.0s, 1.5s, ..., 9.5s
            return (mPeriod - 18 + 2) * .5f;
        } else if (mPeriod < 45) {
            // 10, 11, ..., 19
            return (mPeriod - 36 + 10) * 1f;
        } else {
            // 20, 25, 30, ... 60
            return (mPeriod - 45 + 4) * 5f;
        }
    }

    private void sendToService() {
        Intent intent = new Intent(mContext, NoiseService.class);
        intent.putExtra("spectrum", new SpectrumData(
                mBars, mMinVol / 100f, getPeriodSeconds()));
        mContext.startService(intent);
    }

}
