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

import android.annotation.TargetApi;
import android.content.SharedPreferences;
import android.graphics.PorterDuff.Mode;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentTransaction;
import android.support.v4.view.MenuItemCompat;
import android.support.v7.app.ActionBar;
import android.support.v7.app.ActionBar.OnNavigationListener;
import android.support.v7.app.ActionBarActivity;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.ArrayAdapter;

public class ChromaDoze extends ActionBarActivity implements NoiseServicePercentListener, LockListener, OnNavigationListener {
    private static final int MENU_PLAY_STOP = 1;
    private static final int MENU_LOCK = 2;

    private UIState mUiState;
    private int mFragmentId = FragmentIndex.ID_CHROMA_DOZE;

    private boolean mServiceActive;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);

        mUiState = new UIState(getApplication());

        SharedPreferences pref = getPreferences(MODE_PRIVATE);
        mUiState.loadState(pref);

        // When this Activity is first created, set up the initial fragment.
        // After a save/restore, the framework will drop in the last-used
        // fragment automatically.
        if (savedInstanceState == null) {
            changeFragment(new MainFragment(), false);
        }

        final ActionBar actionBar = getSupportActionBar();
        actionBar.setDisplayShowTitleEnabled(false);
        actionBar.setNavigationMode(ActionBar.NAVIGATION_MODE_LIST);

        ArrayAdapter<String> adapter = new ArrayAdapter<String>(
                actionBar.getThemedContext(),
                R.layout.title_text,
                FragmentIndex.getStrings(this));
        adapter.setDropDownViewResource(android.R.layout.simple_list_item_1);
        actionBar.setListNavigationCallbacks(adapter, this);
    }

    @Override
    public void onResume() {
        super.onResume();
        // Start receiving progress events.
        NoiseService.addPercentListener(this);
        mUiState.addLockListener(this);
    }

    @Override
    protected void onPause() {
        super.onPause();

        // If the equalizer is silent, stop the service.
        // This makes it harder to leave running accidentally.
        if (mServiceActive && mUiState.isSilent()) {
            mUiState.stopSending();
        }

        SharedPreferences.Editor pref = getPreferences(MODE_PRIVATE).edit();
        pref.clear();
        mUiState.saveState(pref);
        pref.commit();

        // Stop receiving progress events.
        NoiseService.removePercentListener(this);
        mUiState.removeLockListener(this);
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuItem mi;

        mi = menu.add(0, MENU_PLAY_STOP, 0, getString(R.string.play_stop));
        MenuItemCompat.setShowAsAction(mi, MenuItemCompat.SHOW_AS_ACTION_ALWAYS);

        if (mFragmentId == FragmentIndex.ID_CHROMA_DOZE) {
            mi = menu.add(0, MENU_LOCK, 0, getString(R.string.lock_unlock));
            MenuItemCompat.setShowAsAction(mi, MenuItemCompat.SHOW_AS_ACTION_ALWAYS);
        }

        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        menu.findItem(MENU_PLAY_STOP)
            .setIcon(mServiceActive ? R.drawable.av_stop : R.drawable.av_play);
        MenuItem mi = menu.findItem(MENU_LOCK);
        if (mi != null) {
            mi.setIcon(getLockIcon());
        }
        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public void onLockStateChange(LockEvent e) {
        // Redraw the lock icon for both event types.
        supportInvalidateOptionsMenu();
    }

    // Get the lock icon which reflects the current action.
    private Drawable getLockIcon() {
        Drawable d = getResources().getDrawable(mUiState.getLocked() ?
                R.drawable.action_unlock : R.drawable.action_lock);
        if (mUiState.getLockBusy()) {
            d.setColorFilter(0xFFFF4444, Mode.SRC_IN);
        } else {
            d.clearColorFilter();
        }
        return d;
    }

    @Override
    public boolean onSupportNavigateUp() {
        // Rewind the back stack.
        getSupportFragmentManager().popBackStack(null, FragmentManager.POP_BACK_STACK_INCLUSIVE);
        return true;
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
        case MENU_LOCK:
            mUiState.toggleLocked();
            supportInvalidateOptionsMenu();
            return true;
        }
        return false;
    }

    @Override
    public void onNoiseServicePercentChange(int percent) {
        boolean newServiceActive = (percent >= 0);
        if (mServiceActive != newServiceActive) {
            mServiceActive = newServiceActive;

            // Redraw the "Play/Stop" button.
            supportInvalidateOptionsMenu();
        }
    }

    private void changeFragment(Fragment f, boolean allowBack) {
        FragmentManager fragmentManager = getSupportFragmentManager();

        // Prune the stack, so "back" always leads home.
        if (fragmentManager.getBackStackEntryCount() > 0) {
            onSupportNavigateUp();
        }

        FragmentTransaction transaction = fragmentManager.beginTransaction();
        transaction.replace(R.id.fragment_container, f);
        if (allowBack) {
            transaction.addToBackStack(null);
            transaction.setTransition(FragmentTransaction.TRANSIT_FRAGMENT_FADE);
        }
        transaction.commit();
    }

    // Fragments can read this >= onActivityCreated().
    public UIState getUIState() {
        return mUiState;
    }

    // Each fragment calls this from onResume to tweak the ActionBar.
    public void setFragmentId(int id) {
        mFragmentId = id;

        final boolean enableUp = id != FragmentIndex.ID_CHROMA_DOZE;
        ActionBar actionBar = getSupportActionBar();
        actionBar.setHomeButtonEnabled(enableUp);
        actionBar.setDisplayHomeAsUpEnabled(enableUp);
        setHomeButtonEnabledCompat(enableUp);
        supportInvalidateOptionsMenu();

        getSupportActionBar().setSelectedNavigationItem(id);
    }

    // HACK: Prevent the icon from remaining clickable after returning to
    //       the main fragment.  Is there a bug in the support library?
    @TargetApi(Build.VERSION_CODES.ICE_CREAM_SANDWICH)
    void setHomeButtonEnabledCompat(boolean enabled) {
        getSupportActionBar().setHomeButtonEnabled(enabled);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
            getActionBar().setHomeButtonEnabled(enabled);
        }
    }

    @Override
    public boolean onNavigationItemSelected(int itemPosition, long itemId) {
        if (itemPosition == mFragmentId) {
            return false;
        }
        switch (itemPosition) {
        case FragmentIndex.ID_CHROMA_DOZE:
            onSupportNavigateUp();
            return true;
        case FragmentIndex.ID_AMP_WAVE:
            changeFragment(new WaveFragment(), true);
            return true;
        case FragmentIndex.ID_ABOUT:
            changeFragment(new AboutFragment(), true);
            return true;
        }
        return false;
    }
}
