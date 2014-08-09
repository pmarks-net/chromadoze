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
import java.util.HashSet;
import java.util.Iterator;

import junit.framework.Assert;
import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Binder;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.PowerManager;
import android.os.RemoteException;
import android.support.v4.app.NotificationCompat;
import android.util.Log;
import android.widget.RemoteViews;

public class NoiseService extends Service {
    public static final int PERCENT_MSG = 1;
    public static final int CONNECT_MSG = 2;

    private SampleShuffler mSampleShuffler;
    private SampleGenerator mSampleGenerator;
    private AudioFocusHelper mAudioFocusHelper = null;

    private static final int NOTIFY_ID = 1;
    private PowerManager.WakeLock mWakeLock;
    
    private ClientHandler mClientHandler;

    private static class ClientHandler extends Handler {
        private final ArrayList<Messenger> mClients;
        private int mLastPercent = -1;
        
        public ClientHandler() {
            mClients = new ArrayList<Messenger>();
        }
        
        // If connected, notify the main activity of our progress.
        public void updatePercent(int percent) {
            notifyClients(null, percent);
        }
        
        @Override
        public void handleMessage(Message msg) {
            Assert.assertEquals(CONNECT_MSG, msg.what);
            Log.e("NoiseService", "Got a connect message!");
            notifyClients(msg.replyTo, null);
        }
        
        private synchronized void notifyClients(Messenger newClient, Integer newPercent) {
            if (newClient != null) {
                mClients.add(newClient);
            }
            if (newPercent != null) {
                mLastPercent = newPercent.intValue();
            }
            for (Iterator<Messenger> it = mClients.iterator(); it.hasNext();) {
                try {
                    it.next().send(Message.obtain(null, PERCENT_MSG, mLastPercent, 0));
                } catch (RemoteException e) {
                    it.remove();
                }
            }
        }
        
    }

    @Override
    public void onCreate() {
        // Set up a message handler in the main thread.
        //mPercentHandler = new PercentHandler();
        mClientHandler = new ClientHandler();
        
        AudioParams params = new AudioParams();
        mSampleShuffler = new SampleShuffler(params);
        mSampleGenerator = new SampleGenerator(this, params, mSampleShuffler);
        PowerManager pm = (PowerManager)getSystemService(Context.POWER_SERVICE);
        mWakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "ChromaDoze Wake Lock");
        mWakeLock.acquire();

        startForeground(NOTIFY_ID, makeNotify());
        
        if (android.os.Build.VERSION.SDK_INT >= 8) {
            // Note: This leaks memory if I use "this" instead of "getApplicationContext()".
            mAudioFocusHelper = new AudioFocusHelper(
                    getApplicationContext(), mSampleShuffler.getVolumeListener());
        }        
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // Handle the notification bar Stop button.
        if (intent.getBooleanExtra("stop", false)) {
            stopSelf();
            return START_NOT_STICKY;
        }

        SpectrumData spectrum = intent.getParcelableExtra("spectrum");

        // Synchronous updates.
        mSampleShuffler.setAmpWave(
                intent.getFloatExtra("minvol", -1),
                intent.getFloatExtra("period", -1));
        mSampleShuffler.getVolumeListener().setVolumeLevel(
                intent.getFloatExtra("volumeLimit", -1));
        if (mAudioFocusHelper != null) {
            mAudioFocusHelper.setActive(
                    !intent.getBooleanExtra("ignoreAudioFocus", false));
        }
                
        // Background updates.
        mSampleGenerator.updateSpectrum(spectrum);
        
        // If the device is under enough memory pressure to kill a foreground
        // service, it's probably best to wait for the user to restart it.
        //
        // Note that switching to START_REDELIVER_INTENT here would probably
        // cause a leak, because we never call stopSelf(startId).  Search for
        // "ActiveServices.java" to see how that works.
        return START_NOT_STICKY;
    }
    
    @Override
    public void onDestroy() {
        mSampleGenerator.stopThread();
        mSampleShuffler.stopThread();
        
        updatePercentAsync(-1);
        mClientHandler = null;
        // XXX Clean up mClientHandler?
        
        if (mAudioFocusHelper != null) {
            mAudioFocusHelper.setActive(false);
        }

        stopForeground(true);
        mWakeLock.release();
        System.exit(0);
    }

    @Override
    public IBinder onBind(Intent intent) {
        // Don't use binding.
        Log.e("NoiseService", "onBind!");
        return new Messenger(mClientHandler).getBinder();
    }

    // Create an icon for the notification bar.
    private Notification makeNotify() {
        // android:launchMode="singleTask" ensures that the latest instance
        // of the Activity will be reachable from the Launcher.  However, a
        // naive Intent can still overwrite the task, so we track down the
        // existing task by pretending to be the Launcher.
        PendingIntent contentIntent = PendingIntent.getActivity(
                this,
                0,
                new Intent(this, ChromaDoze.class)
                        .setAction(Intent.ACTION_MAIN)
                        .addCategory(Intent.CATEGORY_LAUNCHER),
                0);

        Notification n = new NotificationCompat.Builder(this)
                .setSmallIcon(R.drawable.ic_stat_bars)
                .setWhen(0)
                .setContentTitle(getString(R.string.app_name))
                .setContentText(getString(R.string.notification_text))
                .setContentIntent(contentIntent)
                .build();

        // Add a Stop button to the Notification bar.  Not trying to support
        // this pre-ICS, because the click detection and styling are weird.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
            addButtonToNotification(n);
        }
        return n;
    }

    void addButtonToNotification(Notification n) {
        // Create a new RV with a Stop button.
        RemoteViews rv = new RemoteViews(
                getPackageName(), R.layout.notification_with_stop_button);
        PendingIntent pendingIntent = PendingIntent.getService(
                this,
                0,
                new Intent(this, NoiseService.class).putExtra("stop", true),
                0);
        rv.setOnClickPendingIntent(R.id.stop_button, pendingIntent);

        // Insert the original RV into the new one.
        rv.addView(R.id.notification_insert, n.contentView);
        n.contentView = rv;
    }


    public static void sendStopIntent(Context ctx) {
        Intent intent = new Intent(ctx, NoiseService.class);
        ctx.stopService(intent);
    }
    
    public void updatePercentAsync(int percent) {
        mClientHandler.updatePercent(percent);
    }
}
