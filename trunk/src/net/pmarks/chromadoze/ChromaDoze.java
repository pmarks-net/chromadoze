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

public class ChromaDoze extends Activity implements OnClickListener {
    private static final String START_TEXT = "Start";
    private static final String STOP_TEXT = "Stop";
    private static final int MENU_ABOUT = 1;

    private EqualizerView mEqualizer;
    private Button mStopButton;
    
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);
        mEqualizer = (EqualizerView)findViewById(R.id.EqualizerView);
        mStopButton = (Button)findViewById(R.id.StopButton);
        mStopButton.setOnClickListener(this);
        
        SharedPreferences pref = getPreferences(MODE_PRIVATE);
        mEqualizer.loadState(pref);
    }
    
	@Override
	protected void onPause() {
		super.onPause();
		SharedPreferences.Editor pref = getPreferences(MODE_PRIVATE).edit();
		pref.clear();
		mEqualizer.saveState(pref);
		pref.commit();
	}

	@Override
	protected void onResume() {
		super.onResume();
		
		// Do a soft sync-up of the service state.
		boolean active = NoiseService.serviceActive;
		mStopButton.setText(active ? STOP_TEXT : START_TEXT);
		mEqualizer.setSendEnabled(active);
	}

	public void onClick(View v) {
		// Force the service into its expected state.
		if (mStopButton.getText().equals(START_TEXT)) {
			mEqualizer.startSending();
			mStopButton.setText(STOP_TEXT);
		} else {
			mEqualizer.stopSending();
			mStopButton.setText(START_TEXT);
		}
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		menu.add(0, MENU_ABOUT, 0, "About").setIcon(
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


}