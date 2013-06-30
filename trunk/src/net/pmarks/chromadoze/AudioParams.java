package net.pmarks.chromadoze;

import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;

class AudioParams {
    final int STREAM_TYPE = AudioManager.STREAM_MUSIC;
    final int SAMPLE_RATE;
    final int CHANNEL_CONFIG = AudioFormat.CHANNEL_OUT_MONO;
    final int AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT;
    final int LATENCY_MS = 100;
    final int BUF_BYTES;
    final int BUF_SHORTS;

    AudioParams() {
        SAMPLE_RATE = AudioTrack.getNativeOutputSampleRate(STREAM_TYPE);
        BUF_BYTES = Math.max(
                AudioTrack.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT),
                (SAMPLE_RATE * LATENCY_MS / 1000) * 2);
        BUF_SHORTS = BUF_BYTES / 2;
    }

    AudioTrack makeAudioTrack() {
        return new AudioTrack(
                STREAM_TYPE, SAMPLE_RATE, CHANNEL_CONFIG,
                AUDIO_FORMAT, BUF_BYTES, AudioTrack.MODE_STREAM);
    }

}
