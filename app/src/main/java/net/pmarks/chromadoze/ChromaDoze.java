package net.pmarks.chromadoze;

import android.app.backup.BackupManager;
import android.content.SharedPreferences;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.ArrayAdapter;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.Spinner;

import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.graphics.drawable.DrawableCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;

import java.util.Date;
import java.util.Objects;

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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        }
        setContentView(R.layout.main);

        // Prevent system UI from drawing over the app on SDK 35+.
        View mainContainer = findViewById(R.id.main_container);
        ViewCompat.setOnApplyWindowInsetsListener(mainContainer, (v, windowInsets) -> {
            Insets systemBarInsets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBarInsets.left, systemBarInsets.top, systemBarInsets.right, systemBarInsets.bottom);
            return windowInsets;
        });

        mUiState = new UIState(getApplication());

        SharedPreferences pref = getSharedPreferences(PREF_NAME, MODE_PRIVATE);
        mUiState.loadState(pref);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        // With Gesture Navigation enabled, Android steals swipe events from the
        // left and right edges, so we need to add padding there.
        // Note for users: if you don't like the padding, use 3-button navigation.
        FrameLayout gesturePadding = findViewById(R.id.gesture_padding);
        ViewCompat.setOnApplyWindowInsetsListener(gesturePadding, (v, windowInsets) -> {
            Insets gestureInsets = windowInsets.getInsets(WindowInsetsCompat.Type.systemGestures());
            v.setPadding(gestureInsets.left, 0, gestureInsets.right, 0);
            return windowInsets;
        });

        ActionBar actionBar = Objects.requireNonNull(getSupportActionBar());
        actionBar.setDisplayHomeAsUpEnabled(true);
        actionBar.setTitle("");

        mNavSpinner = findViewById(R.id.nav_spinner);
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
        pref.apply();
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
        Drawable d = Objects.requireNonNull(
                ContextCompat.getDrawable(this, mUiState.getLocked() ?
                        R.drawable.action_unlock : R.drawable.action_lock));
        if (mUiState.getLockBusy()) {
            d = DrawableCompat.wrap(d).mutate();
            DrawableCompat.setTint(d, 0xFFFF4444);
            DrawableCompat.setTintMode(d, PorterDuff.Mode.SRC_IN);
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
        ActionBar actionBar = Objects.requireNonNull(getSupportActionBar());
        supportInvalidateOptionsMenu();

        // Use the default left arrow, or a scaled-down ChromaDoze icon.
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
