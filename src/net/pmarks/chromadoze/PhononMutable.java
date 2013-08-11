// Copyright (C) 2013  Paul Marks  http://www.pmarks.net/
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

import java.util.Arrays;
import java.util.Locale;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

// A Phonon represents a collection of user-tweakable sound
// information, presented as a single row in the "Memory" view.
//
// Supported operations:
// - Convert to/from JSON for storage.
// - Efficient equality testing.
// - Convert to SpectrumData for playback.
// - Get and set sound-related values.
public class PhononMutable implements Phonon {
    public static final int BAND_COUNT = SpectrumData.BAND_COUNT;

    // The current value of each bar, [0, 1023]
    private static final short BAR_MAX = 1023;
    private final short mBars[] = new short[BAND_COUNT];

    private int mMinVol = 100;

    public static int PERIOD_MAX = 53;  // Maps to 60 seconds.
    private int mPeriod = 18;  // Maps to 1 second

    private boolean mDirty = true;
    private int mHash;

    public void resetToDefault() {
        for (int i = 0; i < BAND_COUNT; i++) {
            setBar(i, .5f);
        }
        mMinVol = 100;
        mPeriod = 18;
        cleanMe();
    }

    // Load data from <= Chroma Doze 2.2.
    public boolean loadFromLegacyPrefs(SharedPreferences pref) {
        if (pref.getFloat("barHeight0", -1) < 0) {
            return false;
        }
        for (int i = 0; i < BAND_COUNT; i++) {
            setBar(i, pref.getFloat("barHeight" + i, .5f));
        }
        setMinVol(pref.getInt("minVol", 100));
        setPeriod(pref.getInt("period", 18));
        cleanMe();
        return true;
    }

    public boolean loadFromJSON(String jsonString) {
        if (jsonString == null) {
            return false;
        }
        try {
            JSONObject j = new JSONObject(jsonString);
            setMinVol(j.getInt("minvol"));
            setPeriod(j.getInt("period"));
            JSONArray jBars = j.getJSONArray("bars");
            for (int i = 0; i < BAND_COUNT; i++) {
                final int b = jBars.getInt(i);
                if (!(0 <= b && b <= BAR_MAX)) {
                    return false;
                }
                mBars[i] = (short)b;
            }
        } catch (JSONException e) {
            return false;
        }
        cleanMe();
        return true;
    }

    // Storing everything as text might be useful if I ever want
    // to do an export feature.
    @Override
    public String toJSON() {
        try {
            JSONObject j = new JSONObject();
            JSONArray jBars = new JSONArray();
            for (short s : mBars) {
                jBars.put(s);
            }
            j.put("bars", jBars);
            j.put("minvol", mMinVol);
            j.put("period", mPeriod);
            return j.toString();
        } catch (JSONException e) {
            throw new RuntimeException("impossible");
        }
    }

    // band: The index number of the bar.
    // value: Between 0 and 1.
    public void setBar(int band, float value) {
        // Map from 0..1 to a discrete short.
        short sval;
        if (value <= 0f) {
            sval = 0;
        } else if (value >= 1f) {
            sval = BAR_MAX;
        } else {
            sval = (short)(value * BAR_MAX);
        }
        if (mBars[band] != sval) {
            mBars[band] = sval;
            mDirty = true;
        }
    }

    @Override
    public float getBar(int band) {
        return mBars[band] / (float)BAR_MAX;
    }

    private float[] getAllBars() {
        float[] out = new float[BAND_COUNT];
        for (int i = 0; i < BAND_COUNT; i++) {
            out[i] = getBar(i);
        }
        return out;
    }

    // Return true if all equalizer bars are set to zero.
    @Override
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
        if (minVol < 0) {
            minVol = 0;
        } else if (minVol > 100) {
            minVol = 100;
        }
        if (minVol != mMinVol) {
            mMinVol = minVol;
            mDirty = true;
        }
    }

    @Override
    public int getMinVol() {
        return mMinVol;
    }

    @Override
    public String getMinVolText() {
        return mMinVol + "%";
    }

    public void setPeriod(int period) {
        if (period < 0) {
            period = 0;
        } else if (period > PERIOD_MAX) {
            period = PERIOD_MAX;
        }
        if (period != mPeriod) {
            mPeriod = period;
            mDirty = true;
        }
    }

    // This gets the slider position.
    @Override
    public int getPeriod() {
        return mPeriod;
    }

    @Override
    public String getPeriodText() {
        // This probably isn't very i18n friendly.
        float s = getPeriodSeconds();
        if (s >= 1f) {
            return String.format(Locale.getDefault(), "%.2g sec", s);
        } else {
            return String.format(Locale.getDefault(), "%d ms", Math.round(s * 1000));
        }
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

    @Override
    public PhononMutable makeMutableCopy() {
        if (mDirty) {
            throw new IllegalStateException();
        }
        PhononMutable c = new PhononMutable();
        System.arraycopy(mBars, 0, c.mBars, 0, BAND_COUNT);
        c.mMinVol = mMinVol;
        c.mPeriod = mPeriod;
        c.mHash = mHash;
        c.mDirty = false;
        return c;
    }

    // Returns true if any change occurred.
    public boolean sendIfDirty(Context context) {
        if (!mDirty) {
            return false;
        }
        sendToService(context);
        return true;
    }

    @Override
    public void sendToService(Context context) {
        Intent intent = new Intent(context, NoiseService.class);
        intent.putExtra("spectrum", new SpectrumData(
                getAllBars(), mMinVol / 100f, getPeriodSeconds()));
        context.startService(intent);
        cleanMe();
    }

    private void cleanMe() {
        int h = Arrays.hashCode(mBars);
        h = 31 * h + mMinVol;
        h = 31 * h + mPeriod;
        mHash = h;
        mDirty = false;
    }

    @Override
    public boolean fastEquals(Phonon other) {
        PhononMutable o = (PhononMutable)other;
        if (mDirty || o.mDirty) {
            throw new IllegalStateException();
        }
        if (this == o) {
            return true;
        }
        return (mHash == o.mHash &&
                mMinVol == o.mMinVol &&
                mPeriod == o.mPeriod &&
                Arrays.equals(mBars, o.mBars));
    }
}
