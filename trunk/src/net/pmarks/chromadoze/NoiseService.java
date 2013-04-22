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

import junit.framework.Assert;
import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.PowerManager;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.TaskStackBuilder;

public class NoiseService extends Service {
    public static final int PERCENT_MSG = 1;

    // These must be accessed only from the main thread.
    private static int sLastPercent = -1;
    private static NoiseServicePercentListener sPercentListener = null;

    private SampleShuffler mSampleShuffler;
    private SampleGenerator mSampleGenerator;

    private static final int NOTIFY_ID = 1;
    private PowerManager.WakeLock mWakeLock;

    private Handler mPercentHandler;

    private static class PercentHandler extends Handler {
        @Override
        public void handleMessage(Message msg) {
            Assert.assertEquals(PERCENT_MSG, msg.what);
            updatePercent(msg.arg1);
        }
    }

    @Override
    public void onCreate() {
        // Set up a message handler in the main thread.
        mPercentHandler = new PercentHandler();
        mSampleShuffler = new SampleShuffler();
        mSampleGenerator = new SampleGenerator(this, mSampleShuffler);
        PowerManager pm = (PowerManager)getSystemService(Context.POWER_SERVICE);
        mWakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "ChromaDoze Wake Lock");
        mWakeLock.acquire();

        startForeground(NOTIFY_ID, makeNotify());
    }

    @Override
    public void onStart(Intent intent, int startId) {
        SpectrumData spectrum = intent.getParcelableExtra("spectrum");

        // Synchronous updates.
        mSampleShuffler.setAmpWave(spectrum.getMinVol(), spectrum.getPeriod());

        // Background updates.
        mSampleGenerator.updateSpectrum(spectrum);
    }

    @Override
    public void onDestroy() {
        mSampleGenerator.stopThread();
        mSampleShuffler.stopThread();

        mPercentHandler.removeMessages(PERCENT_MSG);
        updatePercent(-1);

        stopForeground(true);
        mWakeLock.release();
    }

    @Override
    public IBinder onBind(Intent intent) {
        // Don't use binding.
        return null;
    }

    // Create an icon for the notification bar.
    private Notification makeNotify() {
        TaskStackBuilder stackBuilder = TaskStackBuilder.create(this);
        stackBuilder.addParentStack(ChromaDoze.class);
        stackBuilder.addNextIntent(new Intent(this, ChromaDoze.class));
        PendingIntent contentIntent = stackBuilder.getPendingIntent(
                0, PendingIntent.FLAG_UPDATE_CURRENT);

        NotificationCompat.Builder mBuilder = new NotificationCompat.Builder(this)
                .setSmallIcon(R.drawable.stat_noise)
                .setContentTitle(getString(R.string.app_name))
                .setContentText(getString(R.string.select_to_configure))
                .setContentIntent(contentIntent);

        return mBuilder.build();
    }

    // Call updatePercent() from any thread.
    public void updatePercentAsync(int percent) {
        mPercentHandler.removeMessages(PERCENT_MSG);
        Message m = Message.obtain(mPercentHandler, PERCENT_MSG);
        m.arg1 = percent;
        m.sendToTarget();
    }

    // If connected, notify the main activity of our progress.
    // This must run in the main thread.
    private static void updatePercent(int percent) {
        if (sPercentListener != null) {
            sPercentListener.onNoiseServicePercentChange(percent);
        }
        sLastPercent = percent;
    }

    // Connect the main activity so it receives progress updates.
    // This must run in the main thread.
    public static void setPercentListener(NoiseServicePercentListener listener) {
        sPercentListener = listener;
        updatePercent(sLastPercent);
    }
}
