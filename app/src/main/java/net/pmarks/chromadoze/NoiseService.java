// Copyright (C) 2010  Paul Marks  http://www.pmarks.net/
//
// This file is part of ChromaDoze.
//
// ChromaDoze is free software: you can redistribute it and/or modify
// it under the terms of the GNU General Public License as published by
// the Free Software Foundation, either version 3 of the License, or
// (at your option) any later version.
//
// ChromaDoze is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
// GNU General Public License for more details.
//
// You should have received a copy of the GNU General Public License
// along with ChromaDoze.  If not, see <http://www.gnu.org/licenses/>.

package net.pmarks.chromadoze;

import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.PowerManager;
import android.os.Parcelable;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.RemoteViews;
import android.widget.TextView;

import androidx.core.app.NotificationChannelCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import java.util.ArrayList;
import java.util.Date;

public class NoiseService extends Service {
    private static final int PERCENT_MSG = 1;

    // These must be accessed only from the main thread.
    private static int sLastPercent = -1;
    private static final ArrayList<PercentListener> sPercentListeners = new ArrayList<>();

    // Save the reason for the most recent stop/restart.  In theory, it would
    // be more correct to use persistent storage, but the values should stick
    // around in RAM long enough for practical purposes.
    private static Date sStopTimestamp = null;
    private static int sStopReasonId = 0;

    private SampleShuffler mSampleShuffler;
    private SampleGenerator mSampleGenerator;
    private AudioFocusHelper mAudioFocusHelper;

    private static final int NOTIFY_ID = 1;
    private PowerManager.WakeLock mWakeLock;
    private static final String CHANNEL_ID = "chromadoze_default";

    private int lastStartId = -1;

    private Handler mPercentHandler;

    private static class PercentHandler extends Handler {

        PercentHandler() {
            super(Looper.getMainLooper());
        }

        @Override
        public void handleMessage(Message msg) {
            if (msg.what != PERCENT_MSG) {
                throw new AssertionError("Unexpected message: " + msg.what);
            }
            updatePercent(msg.arg1);
        }
    }

    @Override
    @SuppressWarnings("WakelockTimeout")
    public void onCreate() {
        // Set up a message handler in the main thread.
        mPercentHandler = new PercentHandler();
        AudioParams params = new AudioParams();
        mSampleShuffler = new SampleShuffler(params);
        mSampleGenerator = new SampleGenerator(this, params, mSampleShuffler);
        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        mWakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "chromadoze:NoiseService");
        mWakeLock.acquire();

        final CharSequence name = getString(R.string.channel_name);
        final String description = getString(R.string.channel_description);
        final int importance = NotificationManagerCompat.IMPORTANCE_LOW;
        NotificationManagerCompat.from(this).createNotificationChannel(
                new NotificationChannelCompat.Builder(CHANNEL_ID, importance)
                        .setName(name)
                        .setDescription(description)
                        .build());

        startForegroundOrRefreshNotification();

        // Note: This leaks memory if I use "this" instead of "getApplicationContext()".
        mAudioFocusHelper = new AudioFocusHelper(
                getApplicationContext(), mSampleShuffler.getVolumeListener());
    }

    @SuppressWarnings("deprecation")
    private static <T extends Parcelable> T getParcelableExtraCompat(Intent intent, String name, Class<T> clazz) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return intent.getParcelableExtra(name, clazz);
        } else {
            return intent.getParcelableExtra(name);
        }
    }

    private void startForegroundOrRefreshNotification() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFY_ID, makeNotify(), ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK);
        } else {
            startForeground(NOTIFY_ID, makeNotify());
        }
    }

    private void stopForegroundCompat() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE);
        } else {
            stopForeground(true);
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // Call startForeground redundantly if we just got POST_NOTIFICATIONS permission.
        if (intent.getBooleanExtra("refreshNotification", false)) {
            startForegroundOrRefreshNotification();
        }

        // When multiple spectra arrive, only the latest should remain active.
        if (lastStartId >= 0) {
            stopSelf(lastStartId);
            lastStartId = -1;
        }

        // Handle the Stop intent.
        int stopReasonId = intent.getIntExtra("stopReasonId", 0);
        if (stopReasonId != 0) {
            saveStopReason(stopReasonId);
            stopSelf(startId);
            return START_NOT_STICKY;
        }

        // Notify the user that the OS restarted the process.
        if ((flags & START_FLAG_REDELIVERY) != 0) {
            saveStopReason(R.string.stop_reason_restarted);
        }

        SpectrumData spectrum = getParcelableExtraCompat(intent, "spectrum", SpectrumData.class);

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


        // If the kernel decides to kill this process, let Android restart it
        // using the most-recent spectrum.  It's important that we call
        // stopSelf() with this startId when a replacement spectrum arrives,
        // or if we're stopping the service intentionally.
        lastStartId = startId;
        return START_REDELIVER_INTENT;
    }

    @Override
    public void onDestroy() {
        if (lastStartId != -1) {
            // This condition can be triggered from adb shell:
            // $ am stopservice net.pmarks.chromadoze/.NoiseService
            saveStopReason(R.string.stop_reason_mysterious);
        }

        mSampleGenerator.stopThread();
        mSampleShuffler.stopThread();

        mPercentHandler.removeMessages(PERCENT_MSG);
        updatePercent(-1);
        mAudioFocusHelper.setActive(false);
        stopForegroundCompat();
        mWakeLock.release();
    }

    @Override
    public IBinder onBind(Intent intent) {
        // Don't use binding.
        return null;
    }

    // Create an icon for the notification bar.
    private Notification makeNotify() {
        NotificationCompat.Builder b = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_stat_bars)
                .setWhen(0)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setContentIntent(PendingIntent.getActivity(
                        this,
                        0,
                        new Intent(this, ChromaDoze.class)
                                .setAction(Intent.ACTION_MAIN)
                                .addCategory(Intent.CATEGORY_LAUNCHER),
                        PendingIntent.FLAG_IMMUTABLE));

        RemoteViews rv = new RemoteViews(
                getPackageName(), R.layout.notification_with_stop_button);
        PendingIntent pendingIntent = PendingIntent.getService(
                this,
                0,
                newStopIntent(this, R.string.stop_reason_notification),
                PendingIntent.FLAG_CANCEL_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        rv.setOnClickPendingIntent(R.id.stop_button, pendingIntent);

        // Temporarily inflate the notification, to copy colors from its default style.
        final View inflated = rv.apply(this, new FrameLayout(this));
        final TextView titleText = inflated.findViewById(R.id.title);
        rv.setInt(R.id.divider, "setBackgroundColor", titleText.getTextColors().getDefaultColor());
        rv.setInt(R.id.stop_button_square, "setBackgroundColor", titleText.getTextColors().getDefaultColor());

        // It would be nice if there were some way to omit the "expander affordance",
        // but this seems good enough.
        b.setCustomContentView(rv);
        b.setStyle(new NotificationCompat.DecoratedCustomViewStyle());

        return b.build();
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
            listener.onNoiseServicePercentChange(percent, sStopTimestamp, sStopReasonId);
        }
        sLastPercent = percent;
    }

    // Connect the main activity so it receives progress updates.
    // This must run in the main thread.
    public static void addPercentListener(PercentListener listener) {
        sPercentListeners.add(listener);
        listener.onNoiseServicePercentChange(sLastPercent, sStopTimestamp, sStopReasonId);
    }

    public static void removePercentListener(PercentListener listener) {
        if (!sPercentListeners.remove(listener)) {
            throw new IllegalStateException();
        }
    }

    public interface PercentListener {
        void onNoiseServicePercentChange(int percent, Date stopTimestamp, int stopReasonId);
    }

    private static Intent newStopIntent(Context ctx, int stopReasonId) {
        return new Intent(ctx, NoiseService.class).putExtra("stopReasonId", stopReasonId);
    }

    public static void stopNow(Context ctx, int stopReasonId) {
        try {
            ctx.startService(newStopIntent(ctx, stopReasonId));
        } catch (IllegalStateException e) {
            // This can be triggered by running "adb shell input keyevent 86" when the app
            // is not running.  We ignore it, because in that case there's nothing to stop.
        }
    }

    private static void saveStopReason(int stopReasonId) {
        sStopTimestamp = new Date();
        sStopReasonId = stopReasonId;
    }
}