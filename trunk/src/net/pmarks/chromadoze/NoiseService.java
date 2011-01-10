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

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

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
    private PowerManager.WakeLock mWakeLock;
    
    private Handler mPercentHandler;
    
    // Fields for {start,stop}ForegroundCompat().  See:
    // http://developer.android.com/resources/samples/ApiDemos/src/com/example/android/apis/app/ForegroundService.html
    @SuppressWarnings("unchecked")
    private static final Class[] mStartForegroundSignature = new Class[] {
        int.class, Notification.class};
    @SuppressWarnings("unchecked")
    private static final Class[] mStopForegroundSignature = new Class[] {
        boolean.class};
    private NotificationManager mNM;
    private Method mStartForeground;
    private Method mStopForeground;
    private Object[] mStartForegroundArgs = new Object[2];
    private Object[] mStopForegroundArgs = new Object[1];
    
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
        PowerManager pm = (PowerManager)getSystemService(Context.POWER_SERVICE);
        mWakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "ChromaDoze Wake Lock");
        mWakeLock.acquire();
        
        // Initialization junk for {start,stop}ForegroundCompat().
        mNM = (NotificationManager)getSystemService(Context.NOTIFICATION_SERVICE);
        try {
            mStartForeground = getClass().getMethod("startForeground",
                    mStartForegroundSignature);
            mStopForeground = getClass().getMethod("stopForeground",
                    mStopForegroundSignature);
        } catch (NoSuchMethodException e) {
            // Running on an older platform.
            mStartForeground = mStopForeground = null;
        }
        
        startForegroundCompat(NOTIFY_ID, makeNotify());
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
        
        stopForegroundCompat(NOTIFY_ID);
        mWakeLock.release();
    }

    @Override
    public IBinder onBind(Intent intent) {
        // Don't use binding.
        return null;
    }
    
    // Create an icon for the notification bar.
    private Notification makeNotify() {
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
        
        return n;
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
    
    /**
     * This is a wrapper around the new startForeground method, using the older
     * APIs if it is not available.
     */
    void startForegroundCompat(int id, Notification notification) {
        // If we have the new startForeground API, then use it.
        if (mStartForeground != null) {
            mStartForegroundArgs[0] = Integer.valueOf(id);
            mStartForegroundArgs[1] = notification;
            try {
                mStartForeground.invoke(this, mStartForegroundArgs);
            } catch (InvocationTargetException e) {
                // Should not happen.
            } catch (IllegalAccessException e) {
                // Should not happen.
            }
            return;
        }

        // Fall back on the old API.
        setForeground(true);
        mNM.notify(id, notification);
    }

    /**
     * This is a wrapper around the new stopForeground method, using the older
     * APIs if it is not available.
     */
    void stopForegroundCompat(int id) {
        // If we have the new stopForeground API, then use it.
        if (mStopForeground != null) {
            mStopForegroundArgs[0] = Boolean.TRUE;
            try {
                mStopForeground.invoke(this, mStopForegroundArgs);
            } catch (InvocationTargetException e) {
                // Should not happen.
            } catch (IllegalAccessException e) {
                // Should not happen.
            }
            return;
        }

        // Fall back on the old API.  Note to cancel BEFORE changing the
        // foreground state, since we could be killed at that point.
        mNM.cancel(id);
        setForeground(false);
    }
}
