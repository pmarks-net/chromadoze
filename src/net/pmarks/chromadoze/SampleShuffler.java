// Copyright (C) 2010  Paul Marks  http://www.pmarks.net/
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
import java.util.Random;

import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.os.Process;

class SampleShuffler {
	public static final int FADE_LEN = 500;
	public static final int AUDIO_BUFFER_LEN = 12288;
	public static final int SAMPLE_RATE = 44100;

	public static final float BASE_AMPLITUDE = 20000;
	public static final float CLIP_AMPLITUDE = 23000;  // 32K/sqrt(2)
	
	private ArrayList<AudioChunk> mAudioChunks = null;
	private Random mRandom = new Random();
	
	private boolean mChunksPurged = false;
	private boolean mStopThread = false;
	
	private float mGlobalVolumeFactor;
	
	private float mFadeInEnvelope[];
	private float mFadeOutEnvelope[];
	
	private PlaybackThread mPlaybackThread;
	
	public SampleShuffler() {
		makeFadeEnvelopes();
		
		// Start playing silence until real data arrives.
		exchangeChunk(new AudioChunk(new float[AUDIO_BUFFER_LEN]));
		
		mPlaybackThread = new PlaybackThread();
		mPlaybackThread.start();
	}
	
	public void stopThread() {
		synchronized (this) {
			mStopThread = true;
		}
		try {
			mPlaybackThread.join();
		} catch (InterruptedException e) {
		}
	}
	
	// Compute the shape of a crossfade curve, on startup only.
	private void makeFadeEnvelopes() {
		mFadeInEnvelope = new float[FADE_LEN];
		mFadeOutEnvelope = new float[FADE_LEN];
		for (int i = 0; i < FADE_LEN; i++) {
			double progress = (double)i / (FADE_LEN - 1);
			mFadeInEnvelope[i] = (float)Math.sin(progress * Math.PI / 2.0);
			mFadeOutEnvelope[i] = (float)Math.cos(progress * Math.PI / 2.0);
		}
	}
	
	private static class ChunksPurged extends Exception {
		private static final long serialVersionUID = -1855423352961625228L;
	}

	private static class StopThread extends Exception {
		private static final long serialVersionUID = 2439290876882896774L;
	}
	
	private class AudioChunk {
		private int mLength;
		private float[] mFloatData;
		private short[] mPcmData;
		private float mMaxAmplitude;

		public AudioChunk(float[] floatData) {
			mFloatData = floatData;
			mLength = mFloatData.length;
			computeMaxAmplitude();
			buildPcmData(BASE_AMPLITUDE / mMaxAmplitude);
		}

		// Figure out the max amplitude of this chunk once. 
		private void computeMaxAmplitude() {
			mMaxAmplitude = 1;  // Prevent division by zero.
			for (float sample : mFloatData) {
				if (sample < 0) sample = -sample;
				if (sample > mMaxAmplitude) mMaxAmplitude = sample;
			}
		}
	
		public float getMaxAmplitude() {
			return mMaxAmplitude;
		}
		
		public void buildPcmData(float volumeFactor) {
			mPcmData = new short[mLength];
			for (int i = 0; i < FADE_LEN; i++) {
				float fadeFactor = mFadeInEnvelope[i];
				mPcmData[i] = (short)(mFloatData[i] * volumeFactor * fadeFactor);
			}
			for (int i = FADE_LEN; i < mLength - FADE_LEN; i++) {
				mPcmData[i] = (short)(mFloatData[i] * volumeFactor);
			}
			for (int i = mLength - FADE_LEN; i < mLength; i++) {
				float fadeFactor = mFadeOutEnvelope[i - (mLength - FADE_LEN)];
				mPcmData[i] = (short)(mFloatData[i] * volumeFactor * fadeFactor);
			}
		}

		public short[] getPcmData() {
			return mPcmData;
		}
		
		public void purgeFloatData() {
			mFloatData = null;
		}
	}
	
	public boolean handleChunk(float[] dctData, int stage) {
		SampleShuffler.AudioChunk newChunk = new AudioChunk(dctData);
		switch (stage) {
		case SampleGeneratorState.S_FIRST_SMALL:
			handleChunkPioneer(newChunk, true);
			return true;
		case SampleGeneratorState.S_OTHER_SMALL:
			handleChunkAdaptVolume(newChunk);
			return true;
		case SampleGeneratorState.S_FIRST_VOLUME:
			handleChunkPioneer(newChunk, false);
			return true;
		case SampleGeneratorState.S_OTHER_VOLUME:
			handleChunkAdaptVolume(newChunk);
			return true;
		case SampleGeneratorState.S_LAST_VOLUME:
			handleChunkFinalizeVolume(newChunk);
			return true;
		case SampleGeneratorState.S_LARGE_NOCLIP:
			return handleChunkNoClip(newChunk);
		}
		throw new RuntimeException("Invalid stage");
	}
	
	// Add a new chunk, deleting all the earlier ones.
	private void handleChunkPioneer(AudioChunk newChunk, boolean notify) {
		mGlobalVolumeFactor = BASE_AMPLITUDE / newChunk.getMaxAmplitude();
		newChunk.buildPcmData(mGlobalVolumeFactor);
		addChunkAndPurge(newChunk, notify);
	}
	
	// Add a new chunk.  If it would clip, make everything quieter.
	private void handleChunkAdaptVolume(AudioChunk newChunk) {
		if (newChunk.getMaxAmplitude() * mGlobalVolumeFactor > CLIP_AMPLITUDE) {
			changeGlobalVolume(newChunk.getMaxAmplitude(), newChunk);
		} else {
			newChunk.buildPcmData(mGlobalVolumeFactor);
			addChunk(newChunk);
		}
	}
	
	// Add a new chunk, and force a max volume that no others can cross.
	private void handleChunkFinalizeVolume(AudioChunk newChunk) {
		float maxAmplitude = newChunk.getMaxAmplitude();
		for (AudioChunk c : mAudioChunks) {
			maxAmplitude = Math.max(maxAmplitude, c.getMaxAmplitude());
		}
		if (maxAmplitude * mGlobalVolumeFactor >= BASE_AMPLITUDE) {
			changeGlobalVolume(maxAmplitude, newChunk);
		} else {
			newChunk.buildPcmData(mGlobalVolumeFactor);
			addChunk(newChunk);
		}
		
		// Delete the now-unused float data, to conserve RAM.
		for (AudioChunk c : mAudioChunks) {
			c.purgeFloatData();
		}
	}
	
	// Add a new chunk.  If it clips, discard it and ask for another.
	private boolean handleChunkNoClip(AudioChunk newChunk) {
		if (newChunk.getMaxAmplitude() * mGlobalVolumeFactor > CLIP_AMPLITUDE) {
			return false;
		} else {
			newChunk.buildPcmData(mGlobalVolumeFactor);
			addChunk(newChunk);
			return true;
		}
	}
	
	// Recompute all chunks with a new volume level.
	// Add a new one first, so the chunk list is never completely empty.
	private void changeGlobalVolume(float maxAmplitude, AudioChunk newChunk) {
		mGlobalVolumeFactor = BASE_AMPLITUDE / maxAmplitude;
		newChunk.buildPcmData(mGlobalVolumeFactor);
		ArrayList<AudioChunk> oldChunks = exchangeChunk(newChunk);
		for (AudioChunk c : oldChunks) {
			c.buildPcmData(mGlobalVolumeFactor);
			addChunk(c);
		}
	}
	
	private synchronized void addChunk(AudioChunk chunk) {
		mAudioChunks.add(chunk);
	}
	
	private synchronized void addChunkAndPurge(AudioChunk chunk, boolean notify) {
		mAudioChunks.clear();
		mAudioChunks.add(chunk);
		if (notify) {
			mChunksPurged = true;
		}
	}
	
	private synchronized ArrayList<AudioChunk> exchangeChunk(AudioChunk chunk) {
		ArrayList<AudioChunk> oldChunks = mAudioChunks;
		mAudioChunks = new ArrayList<AudioChunk>();
		mAudioChunks.add(chunk);
		return oldChunks;
	}
	
	private synchronized short[] getRandomChunk() {
		mChunksPurged = false;
		return mAudioChunks.get(mRandom.nextInt(mAudioChunks.size())).getPcmData();
	}
	
	private synchronized void checkForInterrupts() throws ChunksPurged, StopThread {
		if (mStopThread) {
			throw new StopThread();
		}
		if (mChunksPurged) {
			throw new ChunksPurged();
		}
	}
	
	private class PlaybackThread extends Thread {
		public void run() {
			Process.setThreadPriority(Process.THREAD_PRIORITY_AUDIO);
	        AudioTrack track = new AudioTrack(
	        		AudioManager.STREAM_MUSIC,
	        		SAMPLE_RATE,
	        		AudioFormat.CHANNEL_CONFIGURATION_MONO,
	        		AudioFormat.ENCODING_PCM_16BIT,
	        		AUDIO_BUFFER_LEN,
	        		AudioTrack.MODE_STREAM);
			track.play();

			try {
				playLoop(track);
			} catch (StopThread e) {
			}
			track.release();
		}
		
		private void playLoop(AudioTrack track) throws StopThread {
			short[] curChunk = getRandomChunk();
			short[] buf = new short[0];
			while (true) {
				try {
					// Get a buffer big enough to hold the middle and fade.  For some
					// reason, the audio popped when tried playing the middle in-place.
					int toWrite = curChunk.length - FADE_LEN;
					if (buf.length != toWrite) {
						buf = new short[toWrite];
					}
					
					// Fill in everything but the faded edges.
					int middleLen = curChunk.length - 2 * FADE_LEN;
					System.arraycopy(curChunk, FADE_LEN, buf, 0, middleLen);
					
					// Get the next chunk.
					short[] nextChunk = getRandomChunk();

					// Crossfade by adding the end of this chunk to the start of the next.
					for (int i = 0; i < FADE_LEN; i++) {
						buf[middleLen + i] = (short)(
								curChunk[curChunk.length - FADE_LEN + i] + nextChunk[i]);
					}
					doWrite(track, buf, 0, toWrite);
					curChunk = nextChunk;
				} catch (ChunksPurged e) {
					// Immediately jump to a different chunk.
					// This can produce a slight pop, but it's not worth fixing right now.
					curChunk = getRandomChunk();
				}
			}
		}
		
		// Write some data to the AudioTrack, periodically checking for an interruption. 
		private void doWrite(AudioTrack track, short[] data, int start, int len)
				throws ChunksPurged, StopThread {
			for (int cursor = start; cursor < len;) {
				checkForInterrupts();
				int toWrite = Math.min(AUDIO_BUFFER_LEN / 2, len - cursor);
				track.write(data, cursor, toWrite);
				cursor += toWrite;
			}
		}
	}
}
