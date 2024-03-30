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

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;

import androidx.fragment.app.ListFragment;

import com.mobeta.android.dslv.DragSortListView;
import com.mobeta.android.dslv.DragSortListView.DropListener;
import com.mobeta.android.dslv.DragSortListView.RemoveListener;

import net.pmarks.chromadoze.MemoryArrayAdapter.Saved;

public class MemoryFragment extends ListFragment implements
        OnItemClickListener, DropListener, RemoveListener {

    private View mHeaderView;
    private DragSortListView mDslv;
    private UIState mUiState;

    private MemoryArrayAdapter mAdapter;

    // This is basically the cached result of findScratchCopy().
    private final TrackedPosition mScratchPos = new TrackedPosition();

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
                // Clicked the "Save" button.
                final Phonon ph = mUiState.mScratchPhonon.makeMutableCopy();
                mAdapter.insert(ph, 0);
                // Gray out the header row.
                setScratchPosAndDraw(findScratchCopy());
                // Fake-click the header row.
                onItemClick(null, null, 0, 0);
            }
        });
        mHeaderView = v;
        mDslv.addHeaderView(mHeaderView, null, true);

        mDslv.addHeaderView(
                inflater.inflate(R.layout.memory_list_divider, null), null,
                false);

        return mDslv;
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        mUiState = ((ChromaDoze) getActivity()).getUIState();

        mAdapter = new MemoryArrayAdapter(getActivity(), mUiState.mSavedPhonons);
        setListAdapter(mAdapter);

        mDslv.setOnItemClickListener(this);
        mDslv.setDropListener(this);
        mDslv.setRemoveListener(this);
    }

    @Override
    public void onResume() {
        super.onResume();
        ((ChromaDoze) getActivity()).setFragmentId(FragmentIndex.ID_MEMORY);

        setScratchPosAndDraw(findScratchCopy());
        syncActiveItem(false);
    }

    @Override
    public void drop(int from, int to) {
        if (from != to) {
            Phonon item = mAdapter.getItem(from);
            mAdapter.remove(item);
            mAdapter.insert(item, to);
            moveTrackedPositions(from, to, null);
        }
    }

    @Override
    public void remove(int which) {
        Phonon item = mAdapter.getItem(which);
        mAdapter.remove(item);
        moveTrackedPositions(which, TrackedPosition.NOWHERE, item);
    }

    private void moveTrackedPositions(int from, int to, Phonon deleted) {
        if ((to == TrackedPosition.NOWHERE) != (deleted != null)) {
            throw new IllegalArgumentException();
        }
        try {
            if (mUiState.mActivePos.move(from, to)) {
                // Move the radio button.
                syncActiveItem(false);
            }
        } catch (TrackedPosition.Deleted e) {
            // The active item was deleted!
            // Move it to scratch, so it can keep playing.
            mUiState.mScratchPhonon = deleted.makeMutableCopy();
            setScratchPosAndDraw(-1);
            mUiState.mActivePos.setPos(-1);
            syncActiveItem(true);
            return;
        }

        try {
            mScratchPos.move(from, to);
        } catch (TrackedPosition.Deleted e) {
            // The (inactive) scratch copy was deleted!
            // Reactivate the real scratch.
            setScratchPosAndDraw(-1);
        }
    }

    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position,
                            long id) {
        mUiState.setActivePhonon(position == 0 ?
                -1 : position - mDslv.getHeaderViewsCount());

        if (position == 0) {
            // User clicked on the scratch.  Jump to a copy if one exists.
            syncActiveItem(true);
        }
    }

    // Header row should be grayed out whenever the scratch is redundant.
    private void setScratchPosAndDraw(int pos) {
        mScratchPos.setPos(pos);
        final boolean enabled = (pos == -1);
        mHeaderView.setEnabled(enabled);
        mAdapter.initListItem(mHeaderView, mUiState.mScratchPhonon,
                enabled ? Saved.NO : Saved.YES);
    }

    // If the scratch Phonon is unique, return -1.  Otherwise, return the
    // copy's index within mArray.
    private int findScratchCopy() {
        final PhononMutable scratch = mUiState.mScratchPhonon;
        for (int i = 0; i < mUiState.mSavedPhonons.size(); i++) {
            if (mUiState.mSavedPhonons.get(i).fastEquals(scratch)) {
                return i;
            }
        }
        return -1;
    }

    private void syncActiveItem(boolean scrollThere) {
        // Determine which index to check.
        int index = mUiState.mActivePos.getPos();
        if (index == -1) {
            index = mScratchPos.getPos();
            mUiState.mActivePos.setPos(index);
        }

        // Convert the index to a list row.
        if (index == -1) {
            index = 0;
        } else {
            index += mDslv.getHeaderViewsCount();
        }

        // Modify the UI.
        mDslv.setItemChecked(index, true);
        if (scrollThere) {
            mDslv.smoothScrollToPosition(index);
        }
    }
}