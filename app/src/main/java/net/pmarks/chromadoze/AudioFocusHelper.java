package net.pmarks.chromadoze;

import android.content.ComponentName;
import android.content.Context;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.media.AudioManager.OnAudioFocusChangeListener;
import android.os.Build;

// This file keeps track of AudioFocus events.
// http://developer.android.com/training/managing-audio/audio-focus.html

class AudioFocusHelper implements OnAudioFocusChangeListener {
    private final Context mContext;
    private final SampleShuffler.VolumeListener mVolumeListener;
    private final AudioManager mAudioManager;
    private boolean mActive = false;
    private AudioFocusRequest mRequest;

    public AudioFocusHelper(Context ctx, SampleShuffler.VolumeListener volumeListener) {
        mContext = ctx;
        mVolumeListener = volumeListener;
        mAudioManager = (AudioManager) mContext.getSystemService(Context.AUDIO_SERVICE);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // For Android Oreo (API 26) and above
            mRequest = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                    .setAudioAttributes(AudioParams.makeAudioAttributes())
                    .setOnAudioFocusChangeListener(this)
                    .build();
        }
    }

    public void setActive(boolean active) {
        if (mActive == active) {
            return;
        }
        if (active) {
            requestFocus();
        } else {
            abandonFocus();
        }
        mActive = active;
    }

    @SuppressWarnings("deprecation")
    private void requestFocus() {
        // I'm too lazy to check the return value.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            mAudioManager.requestAudioFocus(mRequest);
        } else {
            mAudioManager.requestAudioFocus(this, AudioParams.STREAM_TYPE, AudioManager.AUDIOFOCUS_GAIN);
        }
    }

    @SuppressWarnings("deprecation")
    private void abandonFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            mAudioManager.abandonAudioFocusRequest(mRequest);
        } else {
            mAudioManager.abandonAudioFocus(this);
        }
    }

    @Override
    public void onAudioFocusChange(int focusChange) {
        switch (focusChange) {
            case AudioManager.AUDIOFOCUS_LOSS:
                // For example, a music player or a sleep timer stealing focus.
                NoiseService.stopNow(mContext, R.string.stop_reason_audiofocus);
                break;
            case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT:
                // For example, an alarm or phone call.
                mVolumeListener.setDuckLevel(SampleShuffler.VolumeListener.DuckLevel.SILENT);
                break;
            case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK:
                // For example, an email notification.
                mVolumeListener.setDuckLevel(SampleShuffler.VolumeListener.DuckLevel.DUCK);
                break;
            case AudioManager.AUDIOFOCUS_GAIN:
                // Resume the default volume level.
                mVolumeListener.setDuckLevel(SampleShuffler.VolumeListener.DuckLevel.NORMAL);
                break;
        }
    }
}
