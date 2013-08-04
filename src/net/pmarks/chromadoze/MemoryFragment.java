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

import java.util.ArrayList;

import android.os.Bundle;
import android.support.v4.app.ListFragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;
import android.widget.Toast;

import com.mobeta.android.dslv.DragSortListView;
import com.mobeta.android.dslv.DragSortListView.DropListener;
import com.mobeta.android.dslv.DragSortListView.RemoveListener;

public class MemoryFragment extends ListFragment implements
        OnItemClickListener, DropListener, RemoveListener {

    private DragSortListView mDslv;
    private UIState mUiState;

    private ArrayAdapter<String> mAdapter;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
        mDslv = (DragSortListView) inflater.inflate(R.layout.memory_list,
                container, false);

        View v = inflater.inflate(R.layout.memory_list_item_top, null);
        View button = v.findViewById(R.id.save_button);
        button.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View arg0) {
                final String message = "Clicked Save";
                Toast.makeText(getActivity(), message, Toast.LENGTH_SHORT)
                        .show();
            }
        });
        mDslv.addHeaderView(v, null, true);

        mDslv.addHeaderView(
                inflater.inflate(R.layout.memory_list_divider, null), null,
                false);

        return mDslv;
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        mUiState = ((ChromaDoze) getActivity()).getUIState();

        ArrayList<String> al = new ArrayList<String>();
        al.add("1");
        al.add("2");
        al.add("3");
        al.add("4");

        mAdapter = new MemoryArrayAdapter(getActivity(),
                R.layout.memory_list_item, R.id.text, al);
        setListAdapter(mAdapter);

        mDslv.setOnItemClickListener(this);
        mDslv.setDropListener(this);
        mDslv.setRemoveListener(this);
    }

    @Override
    public void onResume() {
        super.onResume();
        ((ChromaDoze) getActivity()).setFragmentId(FragmentIndex.ID_MEMORY);

        mDslv.setItemChecked(0, true);
    }

    @Override
    public void drop(int from, int to) {
        if (from != to) {
            final int numHeaders = mDslv.getHeaderViewsCount();
            String item = mAdapter.getItem(from);
            mAdapter.remove(item);
            mAdapter.insert(item, to);
            mDslv.moveCheckState(from + numHeaders, to + numHeaders);
        }
    }

    @Override
    public void remove(int which) {
        final int numHeaders = mDslv.getHeaderViewsCount();
        String item = mAdapter.getItem(which);
        mAdapter.remove(item);
        mDslv.removeCheckState(which + numHeaders);
    }

    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position,
            long id) {
        String message = String.format("Clicked item %d", position);
        Toast.makeText(getActivity(), message, Toast.LENGTH_SHORT).show();
    }
}
