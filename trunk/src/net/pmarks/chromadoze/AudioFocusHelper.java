// Copyright (C) 2014  Paul Marks  http://www.pmarks.net/
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

import android.annotation.TargetApi;
import android.content.ComponentName;
import android.content.Context;
import android.media.AudioManager;
import android.os.Build;

// This file keeps track of AudioFocus events on API >= 8.
// http://developer.android.com/training/managing-audio/audio-focus.html

@TargetApi(Build.VERSION_CODES.FROYO)
public class AudioFocusHelper implements AudioManager.OnAudioFocusChangeListener {
    private final Context mContext;
    private final SampleShuffler.VolumeListener mVolumeListener;
    private final AudioManager mAudioManager;
    private final ComponentName mRemoteControlReceiver;

    public AudioFocusHelper(Context ctx, SampleShuffler.VolumeListener volumeListener) {
        mContext = ctx;
        mVolumeListener = volumeListener;
        mAudioManager = (AudioManager) mContext.getSystemService(Context.AUDIO_SERVICE);
        mRemoteControlReceiver = new ComponentName(mContext, MediaButtonReceiver.class);
    }

    public void requestFocus() {
        mAudioManager.registerMediaButtonEventReceiver(mRemoteControlReceiver);
        // I'm too lazy to check the return value.
        mAudioManager.requestAudioFocus(this, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN);
    }

    public void abandonFocus() {
        mAudioManager.unregisterMediaButtonEventReceiver(mRemoteControlReceiver);
        mAudioManager.abandonAudioFocus(this);
    }

    @Override
    public void onAudioFocusChange(int focusChange) {
        switch(focusChange) {
        case AudioManager.AUDIOFOCUS_LOSS:
            // For example, a music player or a sleep timer stealing focus.
            NoiseService.sendStopIntent(mContext);
            break;
        case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT:
            // For example, an alarm or phone call.
            mVolumeListener.setVolume(SampleShuffler.VolumeListener.VolumeLevel.SILENT);
            break;
        case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK:
            // For example, an email notification.
            mVolumeListener.setVolume(SampleShuffler.VolumeListener.VolumeLevel.DUCK);
            break;
        case AudioManager.AUDIOFOCUS_GAIN:
            // Resume the default volume level.
            mVolumeListener.setVolume(SampleShuffler.VolumeListener.VolumeLevel.NORMAL);
            break;
        }
    }    
}
