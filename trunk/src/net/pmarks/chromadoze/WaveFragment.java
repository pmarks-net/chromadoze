// Copyright (C) 2011  Paul Marks  http://www.pmarks.net/
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
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.SeekBar;
import android.widget.SeekBar.OnSeekBarChangeListener;
import android.widget.TextView;

public class WaveFragment extends Fragment implements OnSeekBarChangeListener {
    private UIState mUiState;
    private SeekBar mMinVolSeek;
    private TextView mMinVolText;
    private SeekBar mPeriodSeek;
    private TextView mPeriodText;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.wave_fragment, container, false);

        mMinVolSeek = (SeekBar) v.findViewById(R.id.MinVolSeek);
        mMinVolText = (TextView) v.findViewById(R.id.MinVolText);
        mPeriodSeek = (SeekBar) v.findViewById(R.id.PeriodSeek);
        mPeriodText = (TextView) v.findViewById(R.id.PeriodText);

        return v;
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        mUiState = ((ChromaDoze)getActivity()).getUIState();

        mMinVolText.setText(mUiState.getMinVolText());
        mMinVolSeek.setProgress(mUiState.getMinVol());
        mMinVolSeek.setOnSeekBarChangeListener(this);

        mPeriodText.setText(mUiState.getPeriodText());
        mPeriodSeek.setProgress(mUiState.getPeriod());
        // When the volume is at 100%, disable the period bar.
        mPeriodSeek.setEnabled(mUiState.getMinVol() != 100);
        mPeriodSeek.setOnSeekBarChangeListener(this);
    }

    @Override
    public void onResume() {
        super.onResume();
        FragmentConfig cfg = new FragmentConfig();
        cfg.title = getString(R.string.amp_wave);
        ((ChromaDoze)getActivity()).setFragmentConfig(cfg);
    }

    @Override
    public void onProgressChanged(SeekBar seekBar, int progress,
            boolean fromUser) {
        if (!fromUser) {
            return;
        }
        if (seekBar == mMinVolSeek) {
            mUiState.setMinVol(progress);
            mMinVolText.setText(mUiState.getMinVolText());
            mPeriodSeek.setEnabled(progress != 100);
        } else if (seekBar == mPeriodSeek) {
            mUiState.setPeriod(progress);
            mPeriodText.setText(mUiState.getPeriodText());
        }
    }

    @Override
    public void onStartTrackingTouch(SeekBar seekBar) {
    }

    @Override
    public void onStopTrackingTouch(SeekBar seekBar) {
    }
}
