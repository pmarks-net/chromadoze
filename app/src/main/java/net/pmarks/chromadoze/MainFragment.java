// Copyright (C) 2013  Paul Marks  http://www.pmarks.net/
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

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import java.text.DateFormat;
import java.util.Date;

public class MainFragment extends Fragment implements NoiseService.PercentListener {
    private EqualizerView mEqualizer;
    private TextView mStateText;
    private ProgressBar mPercentBar;
    private Button mNotificationButton;
    private UIState mUiState;
    private boolean mServiceActive;
    private ActivityResultLauncher<String> mRequestPermission;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mRequestPermission = registerForActivityResult(
                new ActivityResultContracts.RequestPermission(), isGranted -> {
                    // If the user grants POST_NOTIFICATIONS with the service already running,
                    // then tell it to refresh the Notification.
                    if (isGranted) {
                        if (mServiceActive) mUiState.sendToService(true);
                    } else {
                        Toast.makeText(getContext(), "RequestPermission failed", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.main_fragment, container, false);

        mEqualizer = v.findViewById(R.id.EqualizerView);
        mStateText = v.findViewById(R.id.StateText);
        mPercentBar = v.findViewById(R.id.PercentBar);
        mNotificationButton = v.findViewById(R.id.EnableNotificationsButton);
        return v;
    }

    @Override
    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        mUiState = ((ChromaDoze) requireActivity()).getUIState();
        mEqualizer.setUiState(mUiState);

        mNotificationButton.setOnClickListener(v -> {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                mRequestPermission.launch(Manifest.permission.POST_NOTIFICATIONS);
            }
        });
    }

    @Override
    public void onResume() {
        super.onResume();
        // Start receiving progress events.
        NoiseService.addPercentListener(this);
        mUiState.addLockListener(mEqualizer);
        mNotificationButton.setVisibility(hasNotificationPermission() ? View.GONE : View.VISIBLE);
        ((ChromaDoze) requireActivity()).setFragmentId(FragmentIndex.ID_CHROMA_DOZE);
    }

    private boolean hasNotificationPermission() {
        // The POST_NOTIFICATIONS permission is only required on Android 13+.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return ContextCompat.checkSelfPermission(
                    requireContext(), Manifest.permission.POST_NOTIFICATIONS) ==
                    PackageManager.PERMISSION_GRANTED;
        }
        return true;
    }

    @Override
    public void onPause() {
        super.onPause();
        // Stop receiving progress events.
        NoiseService.removePercentListener(this);
        mUiState.removeLockListener(mEqualizer);
    }

    @Override
    public void onNoiseServicePercentChange(int percent, Date stopTimestamp, int stopReasonId) {
        mServiceActive = (percent >= 0);
        boolean showGenerating = false;
        boolean showStopReason = false;
        if (percent < 0) {
            mPercentBar.setVisibility(View.INVISIBLE);
            // While the service is stopped, show what event caused it to stop.
            showStopReason = (stopReasonId != 0);
        } else if (percent < 100) {
            mPercentBar.setVisibility(View.VISIBLE);
            mPercentBar.setProgress(percent);
            showGenerating = true;
        } else {
            mPercentBar.setVisibility(View.INVISIBLE);
            // While the service is active, only the restart event is worth showing.
            showStopReason = (stopReasonId == R.string.stop_reason_restarted);
        }
        if (showStopReason) {
            // Expire the message after 12 hours, to avoid date ambiguity.
            long diff = new Date().getTime() - stopTimestamp.getTime();
            if (diff > 12 * 3600 * 1000L) {
                showStopReason = false;
            }
        }
        if (showGenerating) {
            mStateText.setText(R.string.generating);
        } else if (showStopReason) {
            String timeFmt = DateFormat.getTimeInstance(DateFormat.SHORT).format(stopTimestamp);
            mStateText.setText(timeFmt + ": " + getString(stopReasonId));
        } else {
            mStateText.setText("");
        }
    }
}
