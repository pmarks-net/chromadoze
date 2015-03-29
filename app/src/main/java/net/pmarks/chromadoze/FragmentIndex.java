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
