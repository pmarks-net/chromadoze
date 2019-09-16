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

import androidx.core.content.ContextCompat;

import java.util.ArrayList;

public class UIState {

    private final Context mContext;

    private boolean mLocked = false;
    private boolean mLockBusy = false;
    private final ArrayList<LockListener> mLockListeners = new ArrayList<>();

    public final TrackedPosition mActivePos = new TrackedPosition();
    public PhononMutable mScratchPhonon;
    public ArrayList<Phonon> mSavedPhonons;

    public UIState(Context context) {
        mContext = context;
    }

    private boolean mDirty = false;
    private boolean mAutoPlay;
    private boolean mIgnoreAudioFocus;
    private boolean mVolumeLimitEnabled;
    private int mVolumeLimit;
    public static final int MAX_VOLUME = 100;

    public void saveState(SharedPreferences.Editor pref) {
        pref.putBoolean("locked", mLocked);
        pref.putBoolean("autoPlay", mAutoPlay);
        pref.putBoolean("ignoreAudioFocus", mIgnoreAudioFocus);
        pref.putInt("volumeLimit", getVolumeLimit());
        pref.putString("phononS", mScratchPhonon.toJSON());
        for (int i = 0; i < mSavedPhonons.size(); i++) {
            pref.putString("phonon" + i, mSavedPhonons.get(i).toJSON());
            mSavedPhonons.get(i);
        }
        pref.putInt("activePhonon", mActivePos.getPos());
    }

    public void loadState(SharedPreferences pref) {
        mLocked = pref.getBoolean("locked", false);
        setAutoPlay(pref.getBoolean("autoPlay", false), false);
        setIgnoreAudioFocus(pref.getBoolean("ignoreAudioFocus", false));
        setVolumeLimit(pref.getInt("volumeLimit", MAX_VOLUME));
        setVolumeLimitEnabled(mVolumeLimit != MAX_VOLUME);

        // Load the scratch phonon.
        mScratchPhonon = new PhononMutable();
        if (mScratchPhonon.loadFromJSON(pref.getString("phononS", null))) {
        } else if (mScratchPhonon.loadFromLegacyPrefs(pref)) {
        } else {
            mScratchPhonon.resetToDefault();
        }

        // Load the saved phonons.
        mSavedPhonons = new ArrayList<>();
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

    public void sendToService() {
        Intent intent = new Intent(mContext, NoiseService.class);
        getPhonon().writeIntent(intent);
        intent.putExtra("volumeLimit", (float) getVolumeLimit() / MAX_VOLUME);
        intent.putExtra("ignoreAudioFocus", mIgnoreAudioFocus);
        ContextCompat.startForegroundService(mContext, intent);
        mDirty = false;
    }

    public boolean sendIfDirty() {
        if (mDirty || (mActivePos.getPos() == -1 && mScratchPhonon.isDirty())) {
            sendToService();
            return true;
        }
        return false;
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
        if (!mLocked) throw new AssertionError("Expected mLocked");
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

    // -1 or 0..n
    public void setActivePhonon(int index) {
        if (!(-1 <= index && index < mSavedPhonons.size())) {
            throw new ArrayIndexOutOfBoundsException();
        }
        mActivePos.setPos(index);
        sendToService();
    }

    // This interface is for receiving a callback when the state
    // of the Input Lock has changed.
    public interface LockListener {
        enum LockEvent {TOGGLE, BUSY}

        void onLockStateChange(LockEvent e);
    }

    public void setAutoPlay(boolean enabled, boolean fromUser) {
        mAutoPlay = enabled;
        if (fromUser) {
            // Demonstrate AutoPlay by acting like the Play/Stop button.
            if (enabled) {
                sendToService();
            } else {
                NoiseService.stopNow(mContext, R.string.stop_reason_autoplay);
            }
        }
    }

    public boolean getAutoPlay() {
        return mAutoPlay;
    }

    public void setIgnoreAudioFocus(boolean enabled) {
        if (mIgnoreAudioFocus == enabled) {
            return;
        }
        mIgnoreAudioFocus = enabled;
        mDirty = true;
    }

    public boolean getIgnoreAudioFocus() {
        return mIgnoreAudioFocus;
    }

    public void setVolumeLimitEnabled(boolean enabled) {
        if (mVolumeLimitEnabled == enabled) {
            return;
        }
        mVolumeLimitEnabled = enabled;
        if (mVolumeLimit != MAX_VOLUME) {
            mDirty = true;
        }
    }

    public void setVolumeLimit(int limit) {
        if (limit < 0) {
            limit = 0;
        } else if (limit > MAX_VOLUME) {
            limit = MAX_VOLUME;
        }
        if (mVolumeLimit == limit) {
            return;
        }
        mVolumeLimit = limit;
        if (mVolumeLimitEnabled) {
            mDirty = true;
        }
    }

    public boolean getVolumeLimitEnabled() {
        return mVolumeLimitEnabled;
    }

    public int getVolumeLimit() {
        return mVolumeLimitEnabled ? mVolumeLimit : MAX_VOLUME;
    }
}
