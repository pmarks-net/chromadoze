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

import android.app.Activity;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;

public class ChromaDoze extends Activity implements OnClickListener, NoiseServicePercentListener {
    private static final int MENU_ABOUT = 1;

    private EqualizerView mEqualizer;
    private Button mStopButton;
    private TextView mStateText;
    private ProgressBar mPercentBar;
    
    private String mStartString;
    private String mStopString;

    private boolean mServiceActive;
    
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);
        mEqualizer = (EqualizerView)findViewById(R.id.EqualizerView);
        mStopButton = (Button)findViewById(R.id.StopButton);
        mStopButton.setOnClickListener(this);
        mStateText = (TextView)findViewById(R.id.StateText);
        mPercentBar = (ProgressBar)findViewById(R.id.PercentBar);

        // Get strings.
        mStartString = getString(R.string.start_button);
        mStopString = getString(R.string.stop_button);

        SharedPreferences pref = getPreferences(MODE_PRIVATE);
        mEqualizer.loadState(pref);
    }
    
    @Override
    protected void onPause() {
        super.onPause();
        
        // Stop receiving progress events.
        NoiseService.setPercentListener(null);
        
        SharedPreferences.Editor pref = getPreferences(MODE_PRIVATE).edit();
        pref.clear();
        mEqualizer.saveState(pref);
        pref.commit();
    }

    @Override
    protected void onResume() {
        super.onResume();
        
        // Start receiving progress events.
        NoiseService.setPercentListener(this);
        mEqualizer.setSendEnabled(mServiceActive);
    }

    public void onClick(View v) {
        // Force the service into its expected state.
        if (!mServiceActive) {
            mEqualizer.startSending();
        } else {
            mEqualizer.stopSending();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        menu.add(0, MENU_ABOUT, 0, getString(R.string.about_menu)).setIcon(
                android.R.drawable.ic_menu_info_details);
        return true;
    }
    
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
        case MENU_ABOUT:
            AboutDialog dialog = new AboutDialog(this);
            dialog.show();
            return true;
        }
        return false;
    }
    
    public void onNoiseServicePercentChange(int percent) {
        int vis;
        if (percent < 0) {
            mServiceActive = false;
            vis = View.INVISIBLE;
        } else if (percent < 100) {
            mServiceActive = true;
            mPercentBar.setProgress(percent);
            vis = View.VISIBLE;
        } else {
            mServiceActive = true;
            vis = View.INVISIBLE;
        }
        mPercentBar.setVisibility(vis);
        mStateText.setVisibility(vis);
        mStopButton.setText(mServiceActive ? mStopString : mStartString);
    }
}
