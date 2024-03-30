package net.pmarks.chromadoze;

import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.os.Build;

import androidx.annotation.RequiresApi;

class AudioParams {
    final static int STREAM_TYPE = AudioManager.STREAM_MUSIC;
    final static int CHANNEL_CONFIG = AudioFormat.CHANNEL_OUT_STEREO;
    final static int AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT;
    final static int SHORTS_PER_SAMPLE = 2;  // 16-bit Stereo
    final static int BYTES_PER_SAMPLE = 4;  // 16-bit Stereo
    final static int LATENCY_MS = 100;
    final int SAMPLE_RATE;
    final int BUF_BYTES;
    final int BUF_SAMPLES;

    AudioParams() {
        SAMPLE_RATE = AudioTrack.getNativeOutputSampleRate(STREAM_TYPE);
        BUF_BYTES = Math.max(
                AudioTrack.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT),
                (SAMPLE_RATE * LATENCY_MS / 1000) * BYTES_PER_SAMPLE);
        BUF_SAMPLES = BUF_BYTES / BYTES_PER_SAMPLE;
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    static AudioAttributes makeAudioAttributes() {
        return new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build();
    }

    @SuppressWarnings("deprecation")
    private AudioTrack makeAudioTrackLegacy() {
        return new AudioTrack(
                STREAM_TYPE, SAMPLE_RATE, CHANNEL_CONFIG,
                AUDIO_FORMAT, BUF_BYTES, AudioTrack.MODE_STREAM);
    }

    AudioTrack makeAudioTrack() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            return new AudioTrack(makeAudioAttributes(),
                    new AudioFormat.Builder()
                            .setSampleRate(SAMPLE_RATE)
                            .setChannelMask(CHANNEL_CONFIG)
                            .setEncoding(AUDIO_FORMAT)
                            .build(),
                    BUF_BYTES,
                    AudioTrack.MODE_STREAM,
                    AudioManager.AUDIO_SESSION_ID_GENERATE);
        } else {
            return makeAudioTrackLegacy();
        }
    }
}
