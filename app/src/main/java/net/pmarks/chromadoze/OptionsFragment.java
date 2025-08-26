package net.pmarks.chromadoze;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.SeekBar;
import android.widget.SeekBar.OnSeekBarChangeListener;
import android.widget.TextView;

import androidx.appcompat.widget.SwitchCompat;
import androidx.fragment.app.Fragment;

public class OptionsFragment extends Fragment implements OnSeekBarChangeListener, OnCheckedChangeListener {
    private UIState mUiState;
    private SeekBar mMinVolSeek;
    private TextView mMinVolText;
    private SeekBar mPeriodSeek;
    private TextView mPeriodText;
    private SwitchCompat mAutoPlayCheck;
    private SwitchCompat mIgnoreAudioFocusCheck;
    private SwitchCompat mVolumeLimitCheck;
    private SeekBar mVolumeLimitSeek;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.options_fragment, container, false);

        mMinVolSeek = (SeekBar) v.findViewById(R.id.MinVolSeek);
        mMinVolText = (TextView) v.findViewById(R.id.MinVolText);
        mPeriodSeek = (SeekBar) v.findViewById(R.id.PeriodSeek);
        mPeriodText = (TextView) v.findViewById(R.id.PeriodText);

        mAutoPlayCheck = (SwitchCompat) v.findViewById(R.id.AutoPlayCheck);

        mIgnoreAudioFocusCheck = (SwitchCompat) v.findViewById(R.id.IgnoreAudioFocusCheck);
        mVolumeLimitCheck = (SwitchCompat) v.findViewById(R.id.VolumeLimitCheck);
        mVolumeLimitSeek = (SeekBar) v.findViewById(R.id.VolumeLimitSeek);

        return v;
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        mUiState = ((ChromaDoze) getActivity()).getUIState();
        final Phonon ph = mUiState.getPhonon();

        mMinVolText.setText(ph.getMinVolText());
        mMinVolSeek.setProgress(ph.getMinVol());
        mMinVolSeek.setOnSeekBarChangeListener(this);

        mPeriodText.setText(ph.getPeriodText());
        mPeriodSeek.setProgress(ph.getPeriod());
        // When the volume is at 100%, disable the period bar.
        mPeriodSeek.setEnabled(ph.getMinVol() != 100);
        mPeriodSeek.setMax(PhononMutable.PERIOD_MAX);
        mPeriodSeek.setOnSeekBarChangeListener(this);

        mAutoPlayCheck.setChecked(mUiState.getAutoPlay());
        mAutoPlayCheck.setOnCheckedChangeListener(this);

        mIgnoreAudioFocusCheck.setChecked(mUiState.getIgnoreAudioFocus());
        mIgnoreAudioFocusCheck.setOnCheckedChangeListener(this);

        mVolumeLimitCheck.setOnCheckedChangeListener(this);
        mVolumeLimitSeek.setMax(UIState.MAX_VOLUME);
        mVolumeLimitSeek.setOnSeekBarChangeListener(this);
        redrawVolumeLimit();
    }

    @Override
    public void onResume() {
        super.onResume();
        ((ChromaDoze) getActivity()).setFragmentId(FragmentIndex.ID_OPTIONS);
    }

    @Override
    public void onProgressChanged(SeekBar seekBar, int progress,
                                  boolean fromUser) {
        if (!fromUser) {
            return;
        }
        if (seekBar == mVolumeLimitSeek) {
            mUiState.setVolumeLimit(progress);
            redrawVolumeLimit();
        } else {
            final PhononMutable phm = mUiState.getPhononMutable();
            if (seekBar == mMinVolSeek) {
                phm.setMinVol(progress);
                mMinVolText.setText(phm.getMinVolText());
                mPeriodSeek.setEnabled(progress != 100);
            } else if (seekBar == mPeriodSeek) {
                phm.setPeriod(progress);
                mPeriodText.setText(phm.getPeriodText());
            }
        }
        mUiState.sendIfDirty();
    }

    @Override
    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
        if (buttonView == mAutoPlayCheck) {
            mUiState.setAutoPlay(isChecked, true);
        } else if (buttonView == mIgnoreAudioFocusCheck) {
            mUiState.setIgnoreAudioFocus(isChecked);
        } else if (buttonView == mVolumeLimitCheck) {
            mUiState.setVolumeLimitEnabled(isChecked);
            redrawVolumeLimit();
        }
        mUiState.sendIfDirty();
    }

    @Override
    public void onStartTrackingTouch(SeekBar seekBar) {
    }

    @Override
    public void onStopTrackingTouch(SeekBar seekBar) {
    }

    private void redrawVolumeLimit() {
        boolean enabled = mUiState.getVolumeLimitEnabled();
        mVolumeLimitCheck.setChecked(enabled);
        mVolumeLimitSeek.setVisibility(enabled ? View.VISIBLE : View.INVISIBLE);
        mVolumeLimitSeek.setProgress(mUiState.getVolumeLimit());
    }
}
