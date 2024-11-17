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
    private final List<TimerListener> listeners = new ArrayList<>(); // Store listeners

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
                    notifyTickListeners();
                    timerHandler.postDelayed(this, 1000);
                } else {
                    stopTimer();
                    notifyCompleteListeners();
                }
            }
        };

        timerHandler.post(timerRunnable);
    }

    public void stopTimer() {
        isRunning = false;
        timerHandler.removeCallbacks(timerRunnable);
    }

    public void addListener(TimerListener listener) {
        if (!listeners.contains(listener)) listeners.add(listener);
    }

    public void removeListener(TimerListener listener) {
        listeners.remove(listener);
    }

    private void notifyTickListeners() {
        for (TimerListener listener : listeners) {
            listener.onTimerTick(remainingTime);
        }
    }

    private void notifyCompleteListeners() {
        for (TimerListener listener : listeners) {
            listener.onTimerComplete();
        }
    }

    public boolean isRunning() {
        return isRunning;
    }

    public void changeDuration(int delta) {
        duration = Math.max(60, duration + delta); // Minimum duration of 1 minute
    }

    public int getDuration() {
        return duration;
    }

    public int getRemainingTime() {
        return remainingTime;
    }
}