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
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import java.util.List;

public class MemoryArrayAdapter extends ArrayAdapter<Phonon> {

    enum Saved {YES, NO, NONE}

    public MemoryArrayAdapter(Context context, List<Phonon> objects) {
        super(context, 0, objects);
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        LayoutInflater inflater = (LayoutInflater)
                getContext().getSystemService(Context.LAYOUT_INFLATER_SERVICE);

        View view;
        if (convertView == null) {
            view = inflater.inflate(R.layout.memory_list_item, parent, false);
        } else {
            view = convertView;
        }

        initListItem(view, getItem(position), Saved.NONE);

        return view;

    }

    public void initListItem(View view, Phonon ph, Saved saved) {
        StringBuilder buf = new StringBuilder();
        if (ph.getMinVol() != 100) {
            buf.append(ph.getMinVolText());
            buf.append('\n');
            buf.append(ph.getPeriodText());
            if (saved != Saved.NONE) {
                buf.append('\n');
            }
        }
        if (saved == Saved.YES) {
            buf.append('\u21E9');  // Down arrow.
        } else if (saved == Saved.NO) {
            buf.append(getContext().getString(R.string.unsaved));
        }
        ((TextView) view.findViewById(R.id.text)).setText(buf.toString());
        ((EqualizerViewLite) view.findViewById(R.id.EqualizerView)).setPhonon(ph);
    }
}
