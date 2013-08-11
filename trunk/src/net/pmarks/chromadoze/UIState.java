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

import junit.framework.Assert;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

public class UIState {

    private final Context mContext;

    private boolean mLocked = false;
    private boolean mLockBusy = false;
    private final ArrayList<LockListener> mLockListeners = new ArrayList<LockListener>();

    public final TrackedPosition mActivePos = new TrackedPosition();
    public PhononMutable mScratchPhonon;
    public ArrayList<Phonon> mSavedPhonons;

    public UIState(Context context) {
        mContext = context;
    }

    public void saveState(SharedPreferences.Editor pref) {
        // XXX Save doesn't work yet.
        /*
        for (int i = 0; i < BAND_COUNT; i++) {
            pref.putFloat("barHeight" + i, mBars[i]);
        }
        pref.putInt("minVol", mMinVol);
        pref.putInt("period", mPeriod);
        */
        pref.putBoolean("locked", mLocked);
    }

    public void loadState(SharedPreferences pref) {
        // XXX Load doesn't work yet.
        mLocked = pref.getBoolean("locked", false);

        mActivePos.setPos(-1);
        mScratchPhonon = new PhononMutable();
        if (!mScratchPhonon.loadFromLegacyPrefs(pref)) {
            mScratchPhonon.resetToDefault();
        }

        mSavedPhonons = new ArrayList<Phonon>();
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
        getPhonon().sendToService(mContext);
    }

    public void stopSending() {
        Intent intent = new Intent(mContext, NoiseService.class);
        mContext.stopService(intent);
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

    public Phonon getPhonon() {
        if (mActivePos.getPos() == -1) {
            return mScratchPhonon;
        }
        return mSavedPhonons.get(mActivePos.getPos());
    }

    public PhononMutable getPhononMutable() {
        if (mActivePos.getPos() != -1) {
            mScratchPhonon = mSavedPhonons.get(mActivePos.getPos()).makeMutableCopy();
            mActivePos.setPos(-1);
        }
        return mScratchPhonon;
    }

    public Context getContext() {
        return mContext;
    }

    // -1 or 0..n
    public void setActivePhonon(int index) {
        if (!(-1 <= index && index < mSavedPhonons.size())) {
            throw new ArrayIndexOutOfBoundsException();
        }
        mActivePos.setPos(index);
        getPhonon().sendToService(mContext);
    }
}
