package net.pmarks.chromadoze;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager.NameNotFoundException;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.fragment.app.Fragment;

public class AboutFragment extends Fragment {
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.about_fragment, container, false);

        PackageInfo pinfo;
        try {
            Context context = getActivity();
            pinfo = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
        } catch (NameNotFoundException e) {
            throw new RuntimeException("Can't find package?");
        }

        // Evaluate the format string in VersionText.
        TextView versionText = (TextView) v.findViewById(R.id.VersionText);
        String versionFormat = versionText.getText().toString();
        versionText.setText(String.format(versionFormat, pinfo.versionName));

        return v;
    }

    @Override
    public void onResume() {
        super.onResume();
        ((ChromaDoze) getActivity()).setFragmentId(FragmentIndex.ID_ABOUT);
    }

}
