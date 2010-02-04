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

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.IBinder;
import android.os.PowerManager;
import android.util.Log;

public class NoiseService extends Service {
	
	// Hacky way of testing whether the service is active.
	public static volatile boolean serviceActive = false;
	
	private SampleShuffler mSampleShuffler;
	private SampleGenerator mSampleGenerator;
	
	private static final int NOTIFY_ID = 1;
	private NotificationManager mNotificationManager;
	private PowerManager.WakeLock mWakeLock;
	private boolean mNotificationVisible = false;
	
	@Override
	public void onCreate() {
		serviceActive = true;
		mSampleShuffler = new SampleShuffler();
		mSampleGenerator = new SampleGenerator(mSampleShuffler);
		mNotificationManager =
			(NotificationManager)getSystemService(Context.NOTIFICATION_SERVICE);
		PowerManager pm = (PowerManager)getSystemService(Context.POWER_SERVICE);
		mWakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "ChromaDoze Wake Lock");
	}

	@Override
	public void onStart(Intent intent, int startId) {
		SpectrumData spectrum = intent.getParcelableExtra("spectrum");
		mSampleGenerator.updateSpectrum(spectrum);
		if (!mNotificationVisible) {
			addNotify();
		}
		if (!mWakeLock.isHeld()) {
			mWakeLock.acquire();
		}
	}

	@Override
	public void onDestroy() {
		Log.i("AudioService", "called onDestroy");
		serviceActive = false;
		mSampleGenerator.stopThread();
		mSampleShuffler.stopThread();
		removeNotify();
		mWakeLock.release();
	}

	@Override
	public IBinder onBind(Intent intent) {
		// Don't use binding.
		return null;
	}
	
	private void addNotify() {
		int icon = R.drawable.stat_noise;
		long when = System.currentTimeMillis();
		Notification n = new Notification(icon, null, when);
		n.flags |= Notification.FLAG_ONGOING_EVENT;
		
		Intent intent = new Intent(this, ChromaDoze.class);
		PendingIntent contentIntent = PendingIntent.getActivity(this, 0, intent, 0);
		n.setLatestEventInfo(
				getApplicationContext(),
				"Chroma Doze",
				"Select to configure noise generation.",
				contentIntent);
		mNotificationManager.notify(NOTIFY_ID, n);
		mNotificationVisible = true;
	}
	
	private void removeNotify() {
		mNotificationManager.cancel(NOTIFY_ID);
		mNotificationVisible = false;
	}
}
