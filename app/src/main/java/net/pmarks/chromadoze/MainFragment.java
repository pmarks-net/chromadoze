// Copyright (C) 2013  Paul Marks  http://www.pmarks.net/
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

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;

import java.text.DateFormat;
import java.util.Date;

public class MainFragment extends Fragment implements NoiseService.PercentListener {
    private EqualizerView mEqualizer;
    private TextView mStateText;
    private ProgressBar mPercentBar;
    private UIState mUiState;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.main_fragment, container, false);

        mEqualizer = (EqualizerView) v.findViewById(R.id.EqualizerView);
        mStateText = (TextView) v.findViewById(R.id.StateText);
        mPercentBar = (ProgressBar) v.findViewById(R.id.PercentBar);
        return v;
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        mUiState = ((ChromaDoze) getActivity()).getUIState();
        mEqualizer.setUiState(mUiState);
    }

    @Override
    public void onResume() {
        super.onResume();
        // Start receiving progress events.
        NoiseService.addPercentListener(this);
        mUiState.addLockListener(mEqualizer);

        ((ChromaDoze) getActivity()).setFragmentId(FragmentIndex.ID_CHROMA_DOZE);
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
