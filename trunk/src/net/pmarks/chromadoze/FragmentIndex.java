// Copyright (C) 2013  Paul Marks  http://www.pmarks.net/
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

import android.content.Context;

public class FragmentIndex {
    static final int ID_CHROMA_DOZE = 0;
    static final int ID_AMP_WAVE = 1;
    static final int ID_SAVED_SOUNDS = 2;
    static final int ID_ABOUT = 3;
    static final int ID_COUNT = 4;

    static String[] getStrings(Context context) {
        String[] out = new String[ID_COUNT];
        out[ID_CHROMA_DOZE] = context.getString(R.string.app_name);
        out[ID_AMP_WAVE] = context.getString(R.string.amp_wave);
        out[ID_SAVED_SOUNDS] = context.getString(R.string.saved_sounds);
        out[ID_ABOUT] = context.getString(R.string.about_menu);
        return out;
    }
}
