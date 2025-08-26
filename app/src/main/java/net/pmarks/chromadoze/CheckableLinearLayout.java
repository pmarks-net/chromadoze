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
