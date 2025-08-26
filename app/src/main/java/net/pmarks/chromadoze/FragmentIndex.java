package net.pmarks.chromadoze;

import android.content.Context;

class FragmentIndex {
    static final int ID_CHROMA_DOZE = 0;
    static final int ID_OPTIONS = 1;
    static final int ID_MEMORY = 2;
    static final int ID_ABOUT = 3;
    static final int ID_COUNT = 4;

    static String[] getStrings(Context context) {
        String[] out = new String[ID_COUNT];
        out[ID_CHROMA_DOZE] = getPaddedString(context, R.string.app_name);
        out[ID_OPTIONS] = getPaddedString(context, R.string.options);
        out[ID_MEMORY] = getPaddedString(context, R.string.memory);
        out[ID_ABOUT] = getPaddedString(context, R.string.about_menu);
        return out;
    }

    private static String getPaddedString(Context context, int resId) {
        return context.getString(resId) + "  ";
    }
}
