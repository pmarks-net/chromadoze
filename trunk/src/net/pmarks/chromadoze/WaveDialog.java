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

import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup.LayoutParams;
import android.widget.Button;
import android.widget.SeekBar;
import android.widget.SeekBar.OnSeekBarChangeListener;
import android.widget.TextView;

public class WaveDialog extends Dialog implements OnSeekBarChangeListener {
    private UIState mUiState;
    private SeekBar mMinVolSeek;
    private TextView mMinVolText;
    private SeekBar mPeriodSeek;
    private TextView mPeriodText;

    public WaveDialog(Context context, UIState uiState) {
        super(context);
        mUiState = uiState;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.wave);
        setTitle(R.string.amp_wave);

        getWindow().setLayout(LayoutParams.FILL_PARENT, LayoutParams.WRAP_CONTENT);

        Button okButton = (Button) findViewById(R.id.WaveOkButton);
        okButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                dismiss();
            }
        });

        mMinVolSeek = (SeekBar) findViewById(R.id.MinVolSeek);
        mMinVolSeek.setOnSeekBarChangeListener(this);
        mMinVolSeek.setProgress(mUiState.getMinVol());

        mMinVolText = (TextView) findViewById(R.id.MinVolText);
        mMinVolText.setText(mUiState.getMinVolText());

        mPeriodSeek = (SeekBar) findViewById(R.id.PeriodSeek);
        mPeriodSeek.setOnSeekBarChangeListener(this);
        mPeriodSeek.setProgress(mUiState.getPeriod());

        mPeriodText = (TextView) findViewById(R.id.PeriodText);
        mPeriodText.setText(mUiState.getPeriodText());
    }

    public void onProgressChanged(SeekBar seekBar, int progress,
            boolean fromUser) {
        if (seekBar == mMinVolSeek && mPeriodSeek != null) {
            // Disable the period bar when volume is at 100%.
            mPeriodSeek.setEnabled(progress != 100);
        }
        if (!fromUser) {
            return;
        }
        if (seekBar == mMinVolSeek) {
            mUiState.setMinVol(progress);
            mMinVolText.setText(mUiState.getMinVolText());
        } else if (seekBar == mPeriodSeek) {
            mUiState.setPeriod(progress);
            mPeriodText.setText(mUiState.getPeriodText());
        }
    }

    public void onStartTrackingTouch(SeekBar seekBar) {
    }

    public void onStopTrackingTouch(SeekBar seekBar) {
    }
}
