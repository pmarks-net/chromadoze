// Copyright (C) 2024  Paul Marks  http://www.pmarks.net/
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

import android.os.Handler;

import java.util.ArrayList;
import java.util.List;

public class TimerManager {
    private static TimerManager instance;
    private final Handler timerHandler = new Handler();
    private int duration = 60 * 30; // Default to half an hour
    private int remainingTime = 0; // Time in seconds
    private boolean isRunning = false;
    private Runnable timerRunnable;

    // Lambdas for custom callbacks
    private Runnable tickCallback;
    private Runnable stopCallback;
    private Runnable completeCallback;

    private TimerManager() {
    }

    public static synchronized TimerManager getInstance() {
        if (instance == null) {
            instance = new TimerManager();
        }
        return instance;
    }

    public void startTimer(int durationInSeconds) {
        if (isRunning) return; // Prevent starting multiple timers
        isRunning = true;
        remainingTime = durationInSeconds;

        timerRunnable = new Runnable() {
            @Override
            public void run() {
                if (remainingTime > 0) {
                    remainingTime--;
                    if (tickCallback != null) tickCallback.run();
                    timerHandler.postDelayed(this, 1000);
                } else {
                    stopTimer();
                    if (completeCallback != null) completeCallback.run();
                }
            }
        };

        timerHandler.post(timerRunnable);
    }

    public void stopTimer() {
        isRunning = false;
        timerHandler.removeCallbacks(timerRunnable);
        if (stopCallback != null) stopCallback.run();
    }

    public void setTickCallback(Runnable callback) {
        this.tickCallback = callback;
    }

    public void setStopCallback(Runnable callback) {
        this.stopCallback = callback;
    }

    public void setCompleteCallback(Runnable callback) {
        this.completeCallback = callback;
    }

    public boolean isRunning() {
        return isRunning;
    }

    public int changeDuration(int delta) {
        duration = Math.max(60, duration + delta); // Minimum duration of 1 minute
        return duration;
    }

    public int getDuration() {
        return duration;
    }

    public int getRemainingTime() {
        return remainingTime;
    }
}