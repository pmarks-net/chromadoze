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
        pref.putBoolean("locked", mLocked);
        pref.putString("phononS", mScratchPhonon.toJSON());
        for (int i = 0 ; i < mSavedPhonons.size(); i++) {
            pref.putString("phonon" + i, mSavedPhonons.get(i).toJSON());
            mSavedPhonons.get(i);
        }
        pref.putInt("activePhonon", mActivePos.getPos());
    }

    public void loadState(SharedPreferences pref) {
        mLocked = pref.getBoolean("locked", false);

        // Load the scratch phonon.
        mScratchPhonon = new PhononMutable();
        if (mScratchPhonon.loadFromJSON(pref.getString("phononS", null))) {
        } else if (mScratchPhonon.loadFromLegacyPrefs(pref)) {
        } else {
            mScratchPhonon.resetToDefault();
        }

        // Load the saved phonons.
        mSavedPhonons = new ArrayList<Phonon>();
        for (int i = 0; i < TrackedPosition.NOWHERE; i++) {
            PhononMutable phm = new PhononMutable();
            if (!phm.loadFromJSON(pref.getString("phonon" + i, null))) {
                break;
            }
            mSavedPhonons.add(phm);
        }

        // Load the currently-selected phonon.
        final int active = pref.getInt("activePhonon", -1);
        mActivePos.setPos(-1 <= active && active < mSavedPhonons.size() ?
                active : -1);
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

     // This interface is for receiving a callback when the state
     // of the Input Lock has changed.
     public interface LockListener {
         enum LockEvent { TOGGLE, BUSY };
         void onLockStateChange(LockEvent e);
     }
}
