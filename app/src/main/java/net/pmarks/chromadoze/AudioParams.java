package net.pmarks.chromadoze;

import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;

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

    AudioTrack makeAudioTrack() {
        return new AudioTrack(
                STREAM_TYPE, SAMPLE_RATE, CHANNEL_CONFIG,
                AUDIO_FORMAT, BUF_BYTES, AudioTrack.MODE_STREAM);
    }

}
