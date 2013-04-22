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

import android.app.Dialog;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.actionbarsherlock.app.SherlockActivity;
import com.actionbarsherlock.view.Menu;
import com.actionbarsherlock.view.MenuItem;

public class ChromaDoze extends SherlockActivity implements NoiseServicePercentListener {
    private static final int MENU_PLAY_STOP = 1;
    private static final int MENU_AMPWAVE = 2;
    private static final int MENU_ABOUT = 3;

    private UIState mUiState;
    private EqualizerView mEqualizer;
    private TextView mStateText;
    private ProgressBar mPercentBar;
    private Dialog mActiveDialog;

    private boolean mServiceActive;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);
        mEqualizer = (EqualizerView)findViewById(R.id.EqualizerView);
        mStateText = (TextView)findViewById(R.id.StateText);
        mPercentBar = (ProgressBar)findViewById(R.id.PercentBar);

        mUiState = new UIState(getApplication());

        SharedPreferences pref = getPreferences(MODE_PRIVATE);
        mUiState.loadState(pref);
        mEqualizer.setUiState(mUiState);
    }

    @Override
    protected void onPause() {
        super.onPause();

        // Dismiss the active dialog, if any.
        changeDialog(null);

        // If the equalizer is silent, stop the service.
        // This makes it harder to leave running accidentally.
        if (mServiceActive && mUiState.isSilent()) {
            mUiState.stopSending();
        }

        // Stop receiving progress events.
        NoiseService.setPercentListener(null);

        SharedPreferences.Editor pref = getPreferences(MODE_PRIVATE).edit();
        pref.clear();
        mUiState.saveState(pref);
        pref.commit();
    }

    @Override
    protected void onResume() {
        super.onResume();

        // Start receiving progress events.
        NoiseService.setPercentListener(this);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        menu.add(0, MENU_PLAY_STOP, 0, getString(R.string.play_stop))
            .setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
        menu.add(0, MENU_AMPWAVE, 0, getString(R.string.amp_wave))
            .setIcon(android.R.drawable.ic_menu_manage)
            .setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER);
        menu.add(0, MENU_ABOUT, 0, getString(R.string.about_menu))
            .setIcon(android.R.drawable.ic_menu_info_details)
            .setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER);

        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        MenuItem item = menu.findItem(MENU_PLAY_STOP);
        item.setIcon(mServiceActive ? R.drawable.av_stop : R.drawable.av_play);
        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
        case MENU_PLAY_STOP:
            // Force the service into its expected state.
            if (!mServiceActive) {
                mUiState.startSending();
            } else {
                mUiState.stopSending();
            }
            return true;
        case MENU_AMPWAVE:
            changeDialog(new WaveDialog(this, mUiState));
            return true;
        case MENU_ABOUT:
            changeDialog(new AboutDialog(this));
            return true;
        }
        return false;
    }

    public void onNoiseServicePercentChange(int percent) {
        int vis;
        boolean newServiceActive;
        if (percent < 0) {
            newServiceActive = false;
            vis = View.INVISIBLE;
        } else if (percent < 100) {
            newServiceActive = true;
            mPercentBar.setProgress(percent);
            vis = View.VISIBLE;
        } else {
            newServiceActive = true;
            vis = View.INVISIBLE;
        }
        mPercentBar.setVisibility(vis);
        mStateText.setVisibility(vis);
        if (mServiceActive != newServiceActive) {
            mServiceActive = newServiceActive;
            supportInvalidateOptionsMenu();
        }

    }

    private void changeDialog(Dialog d) {
        if (mActiveDialog != null) {
            mActiveDialog.dismiss();
        }
        mActiveDialog = d;
        if (d != null) {
            d.show();
        }
    }

}
