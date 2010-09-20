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
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.PowerManager;

public class NoiseService extends Service {
    public static final int PERCENT_MSG = 1;
    
    // These must be accessed only from the main thread. 
    private static int sLastPercent = -1;
    private static NoiseServicePercentListener sPercentListener = null;
    
    private SampleShuffler mSampleShuffler;
    private SampleGenerator mSampleGenerator;
    
    private static final int NOTIFY_ID = 1;
    private NotificationManager mNotificationManager;
    private PowerManager.WakeLock mWakeLock;
    
    private Handler mPercentHandler;
    
    @Override
    public void onCreate() {
        // Set up a message handler in the main thread.
        mPercentHandler = new Handler() {
            @Override
            public void handleMessage(Message msg) {
                Assert.assertEquals(PERCENT_MSG, msg.what);
                updatePercent(msg.arg1);
            }
        };
        
        mSampleShuffler = new SampleShuffler();
        mSampleGenerator = new SampleGenerator(this, mSampleShuffler);
        mNotificationManager =
            (NotificationManager)getSystemService(Context.NOTIFICATION_SERVICE);
        PowerManager pm = (PowerManager)getSystemService(Context.POWER_SERVICE);
        mWakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "ChromaDoze Wake Lock");
        mWakeLock.acquire();
        
        addNotify();
    }

    @Override
    public void onStart(Intent intent, int startId) {
        SpectrumData spectrum = intent.getParcelableExtra("spectrum");
        mSampleGenerator.updateSpectrum(spectrum);
    }

    @Override
    public void onDestroy() {
        mSampleGenerator.stopThread();
        mSampleShuffler.stopThread();
        
        mPercentHandler.removeMessages(PERCENT_MSG);
        updatePercent(-1);
        
        removeNotify();
        mWakeLock.release();
    }

    @Override
    public IBinder onBind(Intent intent) {
        // Don't use binding.
        return null;
    }
    
    // Display an icon in the notification bar.
    private void addNotify() {
        int icon = R.drawable.stat_noise;
        long when = System.currentTimeMillis();
        Notification n = new Notification(icon, null, when);
        n.flags |= Notification.FLAG_ONGOING_EVENT;
        
        Intent intent = new Intent(this, ChromaDoze.class);
        PendingIntent contentIntent = PendingIntent.getActivity(this, 0, intent, 0);
        n.setLatestEventInfo(
                getApplicationContext(),
                getString(R.string.app_name),
                getString(R.string.select_to_configure),
                contentIntent);
        mNotificationManager.notify(NOTIFY_ID, n);
    }
    
    private void removeNotify() {
        mNotificationManager.cancel(NOTIFY_ID);
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
