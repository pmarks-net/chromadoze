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

import android.annotation.SuppressLint;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;

public class TimerFragment extends Fragment implements TimerListener {
    private TextView mDurationText;
    private TextView mTimeSubheading;
    private Button mMinus1, mMinus5, mMinus10, mPlus1, mPlus5, mPlus10, mStartStop;
    private final TimerManager timer = TimerManager.getInstance();

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.timer_fragment, container, false);

        mDurationText = v.findViewById(R.id.DurationText);
        mTimeSubheading = v.findViewById(R.id.TimeSubheading);
        mMinus1 = v.findViewById(R.id.Minus1);
        mMinus5 = v.findViewById(R.id.Minus5);
        mMinus10 = v.findViewById(R.id.Minus10);
        mPlus1 = v.findViewById(R.id.Plus1);
        mPlus5 = v.findViewById(R.id.Plus5);
        mPlus10 = v.findViewById(R.id.Plus10);
        mStartStop = v.findViewById(R.id.StartStop);

        setupTimerButtons();
        setupStartButton();

        return v;
    }

    @Override
    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        updateText(timer.getRemainingTime()); // Initial UI update
    }

    private void setupTimerButtons() {
        mMinus1.setOnClickListener(v -> changeTimerDuration(-1));
        mMinus5.setOnClickListener(v -> changeTimerDuration(-5));
        mMinus10.setOnClickListener(v -> changeTimerDuration(-10));
        mPlus1.setOnClickListener(v -> changeTimerDuration(1));
        mPlus5.setOnClickListener(v -> changeTimerDuration(5));
        mPlus10.setOnClickListener(v -> changeTimerDuration(10));
    }

    private void changeTimerDuration(int minutes) {
        timer.changeDuration(minutes * 60);
        updateText(timer.getRemainingTime());
    }

    private void setupStartButton() {
        mStartStop.setOnClickListener(v -> startStopTimer());
    }

    private void startStopTimer() {
        if (timer.isRunning()) {
            timer.stopTimer();
            updateText(0);
        } else {
            timer.startTimer(timer.getDuration());
        }
    }

    @SuppressLint("DefaultLocale")
    private void updateText(int remainingTime) {
        if (timer.isRunning()) {
            int minutes = remainingTime / 60;
            int seconds = remainingTime % 60;
            mDurationText.setText(String.format("%d:%02d", minutes, seconds));
            mStartStop.setText("Stop");
            mTimeSubheading.setText("Time left");
        } else {
            mDurationText.setText(String.valueOf(timer.getDuration() / 60));
            mStartStop.setText("Start");
            mTimeSubheading.setText("Minutes");
        }
    }

    @Override
    public void onTimerTick(int remainingTime) {
        updateText(remainingTime); // Update UI every tick
    }

    @Override
    public void onTimerComplete() {
        updateText(0); // Reset UI on timer finish
        NoiseService.stopNow(requireActivity().getApplication(), R.string.stop_reason_timer);
    }

    @Override
    public void onResume() {
        super.onResume();
        timer.addListener(this); // Register as a listener
        ((ChromaDoze) getActivity()).setFragmentId(FragmentIndex.ID_TIMER);
    }

    @Override
    public void onPause() {
        super.onPause();
        timer.removeListener(this); // Deregister as a listener
    }
}
