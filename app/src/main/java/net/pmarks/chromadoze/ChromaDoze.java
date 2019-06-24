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

import android.app.backup.BackupManager;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.PorterDuff.Mode;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.ArrayAdapter;
import android.widget.ImageButton;
import android.widget.Spinner;

import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.ContextCompat;
import androidx.core.view.MenuItemCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;

import java.util.Date;

public class ChromaDoze extends AppCompatActivity implements
        NoiseService.PercentListener, UIState.LockListener, OnItemSelectedListener {
    private static final int MENU_PLAY_STOP = 1;
    private static final int MENU_LOCK = 2;

    private UIState mUiState;
    private int mFragmentId = FragmentIndex.ID_CHROMA_DOZE;

    private Drawable mToolbarIcon;
    private Spinner mNavSpinner;

    private boolean mServiceActive;

    // The name to use when accessing our SharedPreferences.
    public static final String PREF_NAME = "ChromaDoze";

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);

        mUiState = new UIState(getApplication());

        SharedPreferences pref = getSharedPreferences(PREF_NAME, MODE_PRIVATE);
        mUiState.loadState(pref);

        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        ActionBar actionBar = getSupportActionBar();
        actionBar.setDisplayHomeAsUpEnabled(true);
        actionBar.setTitle("");

        mNavSpinner = (Spinner) findViewById(R.id.nav_spinner);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                actionBar.getThemedContext(), R.layout.spinner_title,
                FragmentIndex.getStrings(this));
        adapter.setDropDownViewResource(R.layout.support_simple_spinner_dropdown_item);
        mNavSpinner.setAdapter(adapter);
        mNavSpinner.setOnItemSelectedListener(this);


        // Created a scaled-down icon for the Toolbar.
        {
            TypedValue tv = new TypedValue();
            getTheme().resolveAttribute(R.attr.actionBarSize, tv, true);
            int height = TypedValue.complexToDimensionPixelSize(tv.data, getResources().getDisplayMetrics());
            // This originally used a scaled-down launcher icon, but I don't feel like figuring
            // out how to render R.mipmap.chromadoze_icon correctly.
            mToolbarIcon = ContextCompat.getDrawable(this, R.drawable.toolbar_icon);
        }

        // When this Activity is first created, set up the initial fragment.
        // After a save/restore, the framework will drop in the last-used
        // fragment automatically.
        if (savedInstanceState == null) {
            changeFragment(new MainFragment(), false);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        // Start receiving progress events.
        NoiseService.addPercentListener(this);
        mUiState.addLockListener(this);

        if (mUiState.getAutoPlay()) {
            mUiState.sendToService();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();

        // If the equalizer is silent, stop the service.
        // This makes it harder to leave running accidentally.
        if (mServiceActive && mUiState.getPhonon().isSilent()) {
            NoiseService.stopNow(getApplication(), R.string.stop_reason_silent);
        }

        SharedPreferences.Editor pref = getSharedPreferences(PREF_NAME, MODE_PRIVATE).edit();
        pref.clear();
        mUiState.saveState(pref);
        pref.commit();
        new BackupManager(this).dataChanged();

        // Stop receiving progress events.
        NoiseService.removePercentListener(this);
        mUiState.removeLockListener(this);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        menu.add(0, MENU_PLAY_STOP, 0, getString(R.string.play_stop)).setShowAsAction(
                MenuItem.SHOW_AS_ACTION_ALWAYS);

        if (mFragmentId == FragmentIndex.ID_CHROMA_DOZE) {
            menu.add(0, MENU_LOCK, 0, getString(R.string.lock_unlock)).setShowAsAction(
                    MenuItem.SHOW_AS_ACTION_ALWAYS);
        }

        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        menu.findItem(MENU_PLAY_STOP).setIcon(
                mServiceActive ? R.drawable.av_stop : R.drawable.av_play);
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
        Drawable d = ContextCompat.getDrawable(this, mUiState.getLocked() ?
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
        getSupportFragmentManager().popBackStack(null,
                FragmentManager.POP_BACK_STACK_INCLUSIVE);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case MENU_PLAY_STOP:
                // Force the service into its expected state.
                if (!mServiceActive) {
                    mUiState.sendToService();
                } else {
                    NoiseService.stopNow(getApplication(), R.string.stop_reason_toolbar);
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
    public void onNoiseServicePercentChange(int percent, Date stopTimestamp, int stopReasonId) {
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
            transaction
                    .setTransition(FragmentTransaction.TRANSIT_NONE);
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
        supportInvalidateOptionsMenu();

        // Use the default left arrow, or a scaled-down Chroma Doze icon.
        actionBar.setHomeAsUpIndicator(enableUp ? null : mToolbarIcon);

        // When we're on the main page, make the icon non-clickable.
        ImageButton navUp = findImageButton(findViewById(R.id.toolbar));
        if (navUp != null) {
            navUp.setClickable(enableUp);
        }

        mNavSpinner.setSelection(id);
    }

    // Search a View for the first ImageButton.  We use it to locate the
    // home/up button in a Toolbar.
    private static ImageButton findImageButton(View view) {
        if (view instanceof ImageButton) {
            return (ImageButton) view;
        } else if (view instanceof ViewGroup) {
            ViewGroup vg = (ViewGroup) view;
            for (int i = 0; i < vg.getChildCount(); i++) {
                ImageButton found = findImageButton(vg.getChildAt(i));
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    // Handle nav_spinner selection.
    @Override
    public void onItemSelected(AdapterView<?> parent, View view, int position,
                               long id) {
        if (position == mFragmentId) {
            return;
        }
        switch (position) {
            case FragmentIndex.ID_CHROMA_DOZE:
                onSupportNavigateUp();
                return;
            case FragmentIndex.ID_OPTIONS:
                changeFragment(new OptionsFragment(), true);
                return;
            case FragmentIndex.ID_MEMORY:
                changeFragment(new MemoryFragment(), true);
                return;
            case FragmentIndex.ID_ABOUT:
                changeFragment(new AboutFragment(), true);
                return;
        }
    }

    @Override
    public void onNothingSelected(AdapterView<?> parent) {
    }
}
