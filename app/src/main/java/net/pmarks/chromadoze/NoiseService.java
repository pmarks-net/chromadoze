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
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.PowerManager;
import android.support.v4.app.NotificationCompat;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.RemoteViews;
import android.widget.TextView;

import junit.framework.Assert;

import java.util.ArrayList;

public class NoiseService extends Service {
    private static final int PERCENT_MSG = 1;

    // These must be accessed only from the main thread.
    private static int sLastPercent = -1;
    private static final ArrayList<PercentListener> sPercentListeners = new ArrayList<>();

    private SampleShuffler mSampleShuffler;
    private SampleGenerator mSampleGenerator;
    private AudioFocusHelper mAudioFocusHelper;

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
        AudioParams params = new AudioParams();
        mSampleShuffler = new SampleShuffler(params);
        mSampleGenerator = new SampleGenerator(this, params, mSampleShuffler);
        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        mWakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "ChromaDoze Wake Lock");
        mWakeLock.acquire();

        startForeground(NOTIFY_ID, makeNotify());

        // Note: This leaks memory if I use "this" instead of "getApplicationContext()".
        mAudioFocusHelper = new AudioFocusHelper(
                getApplicationContext(), mSampleShuffler.getVolumeListener());
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
        mAudioFocusHelper.setActive(
                !intent.getBooleanExtra("ignoreAudioFocus", false));

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

        mPercentHandler.removeMessages(PERCENT_MSG);
        updatePercent(-1);
        mAudioFocusHelper.setActive(false);
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
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .build();

        // Add a Stop button to the Notification bar.  Not trying to support
        // this pre-ICS, because the click detection and styling are weird.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
            addButtonToNotification(n);
        }
        return n;
    }

    private void addButtonToNotification(Notification n) {
        // Create a new RV with a Stop button.
        RemoteViews rv = new RemoteViews(
                getPackageName(), R.layout.notification_with_stop_button);
        PendingIntent pendingIntent = PendingIntent.getService(
                this,
                0,
                new Intent(this, NoiseService.class).putExtra("stop", true),
                0);
        rv.setOnClickPendingIntent(R.id.stop_button, pendingIntent);

        // Pre-render the original RV, and copy some of the colors.
        final View inflated = n.contentView.apply(this, new FrameLayout(this));
        final TextView titleText = findTextView(inflated, getString(R.string.app_name));
        final TextView defaultText = findTextView(inflated, getString(R.string.notification_text));
        rv.setInt(R.id.divider, "setBackgroundColor", defaultText.getTextColors().getDefaultColor());
        rv.setInt(R.id.stop_button_square, "setBackgroundColor", titleText.getTextColors().getDefaultColor());

        // Insert a copy of the original RV into the new one.
        rv.addView(R.id.notification_insert, n.contentView.clone());

        // Splice everything back into the original's root view.
        int id = Resources.getSystem().getIdentifier("status_bar_latest_event_content", "id", "android");
        n.contentView.removeAllViews(id);
        n.contentView.addView(id, rv);
    }

    private static TextView findTextView(View view, String title) {
        if (view instanceof TextView) {
            TextView text = (TextView) view;
            if (text.getText().equals(title)) {
                return text;
            }
        } else if (view instanceof ViewGroup) {
            ViewGroup vg = (ViewGroup) view;
            for (int i = 0; i < vg.getChildCount(); i++) {
                TextView found = findTextView(vg.getChildAt(i), title);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
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
        for (PercentListener listener : sPercentListeners) {
            listener.onNoiseServicePercentChange(percent);
        }
        sLastPercent = percent;
    }

    // Connect the main activity so it receives progress updates.
    // This must run in the main thread.
    public static void addPercentListener(PercentListener listener) {
        sPercentListeners.add(listener);
        listener.onNoiseServicePercentChange(sLastPercent);
    }

    public static void removePercentListener(PercentListener listener) {
        if (!sPercentListeners.remove(listener)) {
            throw new IllegalStateException();
        }
    }

    public interface PercentListener {
        void onNoiseServicePercentChange(int percent);
    }

    public static void sendStopIntent(Context ctx) {
        Intent intent = new Intent(ctx, NoiseService.class);
        ctx.stopService(intent);
    }
}
