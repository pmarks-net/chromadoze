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

import android.app.Activity;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;

public class MainFragment extends Fragment implements NoiseServicePercentListener {
    private EqualizerView mEqualizer;
    private TextView mStateText;
    private ProgressBar mPercentBar;
    private UIState mUiState;

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        mUiState = ((ChromaDoze)activity).getUIState();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.main_fragment, container, false);

        mEqualizer = (EqualizerView)v.findViewById(R.id.EqualizerView);
        mStateText = (TextView)v.findViewById(R.id.StateText);
        mPercentBar = (ProgressBar)v.findViewById(R.id.PercentBar);

        mEqualizer.setUiState(mUiState);

        return v;
    }

    @Override
    public void onResume() {
        super.onResume();
        // Start receiving progress events.
        NoiseService.addPercentListener(this);
        mUiState.addLockListener(mEqualizer);

        FragmentConfig cfg = new FragmentConfig();
        cfg.isMain = true;
        cfg.title = getString(R.string.app_name);
        ((ChromaDoze)getActivity()).setFragmentConfig(cfg);
    }

    @Override
    public void onPause() {
        super.onPause();
        // Stop receiving progress events.
        NoiseService.removePercentListener(this);
        mUiState.removeLockListener(mEqualizer);
    }

    @Override
    public void onNoiseServicePercentChange(int percent) {
        int vis;
        if (percent < 0) {
            vis = View.INVISIBLE;
        } else if (percent < 100) {
            mPercentBar.setProgress(percent);
            vis = View.VISIBLE;
        } else {
            vis = View.INVISIBLE;
        }
        mPercentBar.setVisibility(vis);
        mStateText.setVisibility(vis);
    }
}
