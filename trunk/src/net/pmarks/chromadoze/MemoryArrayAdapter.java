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

import java.util.List;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

public class MemoryArrayAdapter extends ArrayAdapter<String> {

    private final Context mContext;
    private final UIState mUiState;

    public MemoryArrayAdapter(Context context, int resource,
            int textViewResourceId, List<String> objects,
            UIState uiState) {
        super(context, resource, textViewResourceId, objects);
        mContext = context;
        mUiState = uiState;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        LayoutInflater inflater = (LayoutInflater) mContext
                .getSystemService(Context.LAYOUT_INFLATER_SERVICE);

        View view;
        if (convertView == null) {
            view = inflater.inflate(R.layout.memory_list_item, parent, false);
        } else {
            view = convertView;
        }

        initListItem(view, getItem(position));

        return view;

    }

    public void initListItem(View view, String text) {
        TextView tv = (TextView) view.findViewById(R.id.text);
        tv.setText(text);

        EqualizerViewLite eq = (EqualizerViewLite) view.findViewById(R.id.EqualizerView);
        eq.setUiState(mUiState);
    }
}
