// Copyright (C) 2013  Paul Marks  http://www.pmarks.net/
//
// This file is part of ChromaDoze.
//
// ChromaDoze is free software: you can redistribute it and/or modify
// it under the terms of the GNU General Public License as published by
// the Free Software Foundation, either version 3 of the License, or
// (at your option) any later version.
//
// ChromaDoze is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
// GNU General Public License for more details.
//
// You should have received a copy of the GNU General Public License
// along with ChromaDoze.  If not, see <http://www.gnu.org/licenses/>.

package net.pmarks.chromadoze;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.Checkable;
import android.widget.LinearLayout;

public class CheckableLinearLayout extends LinearLayout implements Checkable {

    private Checkable mChild;

    public CheckableLinearLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        for (int i = 0; i < getChildCount(); i++) {
            try {
                mChild = (Checkable) getChildAt(i);
                return;
            } catch (ClassCastException e) {
            }
        }
    }

    @Override
    public boolean isChecked() {
        return mChild.isChecked();
    }

    @Override
    public void setChecked(boolean checked) {
        mChild.setChecked(checked);
    }

    @Override
    public void toggle() {
        mChild.toggle();
    }

    @Override
    public void setEnabled(boolean enabled) {
        super.setEnabled(enabled);
        for (int i = 0; i < getChildCount(); i++) {
            getChildAt(i).setEnabled(enabled);
        }
    }
}
