// Copyright (C) 2010  Paul Marks  http://www.pmarks.net/
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
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.MotionEvent;

public class EqualizerView extends android.view.View implements LockListener {
    public static final int BAND_COUNT = SpectrumData.BAND_COUNT;

    private final Paint mBarColor[] = new Paint[BAND_COUNT];
    private final Paint mBaseColor[] = new Paint[4];
    private final Paint mWhite = new Paint();

    private UIState mUiState;

    private float mWidth;
    private float mHeight;
    private float mBarWidth;
    private float mZeroLineY;

    public EqualizerView(Context context, AttributeSet attrs) {
        super(context, attrs);
        makeColors();
    }

    public void setUiState(UIState uiState) {
        mUiState = uiState;
        invalidate();
    }

    private void makeColors() {
        Bitmap bmp = BitmapFactory.decodeResource(getResources(), R.drawable.spectrum);
        for (int i = 0; i < BAND_COUNT; i++) {
            Paint p = new Paint();
            int x = (bmp.getWidth() - 1) * i / (BAND_COUNT - 1);
            p.setColor(bmp.getPixel(x, 0));
            mBarColor[i] = p;
        }

        int i = 0;
        for (int v : new int[]{100, 75, 55, 50}) {
            Paint p = new Paint();
            p.setColor(Color.rgb(v, v,v));
            mBaseColor[i++] = p;
        }

        mWhite.setColor(Color.WHITE);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        final boolean isLocked = mUiState != null ? mUiState.getLocked() : false;
        for (int i = 0; i < BAND_COUNT; i++) {
            float bar = mUiState != null ? mUiState.getBar(i) : .5f;
            float startX = mBarWidth * i;
            float stopX = startX + mBarWidth;
            float startY = barToY(bar);
            float midY = startY + mBarWidth;

            if (bar > 0) {
                canvas.drawRect(startX, startY, stopX, midY, mBarColor[i]);
            }

            // Lower the brightness and contrast when locked.
            canvas.drawRect(startX, midY, stopX, mHeight,
                    mBaseColor[i % 2 + (isLocked ? 2 : 0)]);
        }

        canvas.drawLine(0, mZeroLineY, mWidth, mZeroLineY, mWhite);
    }

    private float mLastX;
    private float mLastY;

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (mUiState.getLocked()) {
            switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                mUiState.setLockBusy(true);
                return true;
            case MotionEvent.ACTION_UP:
                mUiState.setLockBusy(false);
                return true;
            case MotionEvent.ACTION_MOVE:
                return true;
            }
            return false;
        }

        switch (event.getAction()) {
        case MotionEvent.ACTION_DOWN:
            mLastX = event.getX();
            mLastY = event.getY();
            break;
        case MotionEvent.ACTION_UP:
        case MotionEvent.ACTION_MOVE:
            break;
        default:
            return false;
        }

        for (int i = 0; i < event.getHistorySize(); i++) {
            touchLine(event.getHistoricalX(i), event.getHistoricalY(i));
        }
        touchLine(event.getX(), event.getY());

        if (mUiState.commitBars()) {
            invalidate();
        }
        return true;
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        mWidth = getWidth();
        mHeight = getHeight();
        mBarWidth = mWidth / BAND_COUNT;
        mZeroLineY = mHeight * .9f;
    }

    private float yToBar(float y) {
        float barHeight = 1f - (y / (mZeroLineY - mBarWidth));
        if (barHeight < 0) {
            return 0;
        }
        if (barHeight > 1) {
            return 1;
        }
        return barHeight;
    }

    private float barToY(float barHeight) {
        return (1f - barHeight) * (mZeroLineY - mBarWidth);
    }

    private int getBarIndex(float x) {
        int out = (int)(x / mBarWidth);
        if (out < 0) {
            out = 0;
        }
        if (out > BAND_COUNT - 1) {
            out = BAND_COUNT - 1;
        }
        return out;
    }

    // Starting bar?
    // Ending bar?
    // For each bar it exits:
    //   set Y to exit-Y.
    // For the ending point:
    //   set Y to final-Y.

    // Exits:
    //   Right:
    //     0->3: 0, 1, 2 [endpoint in 3]
    //   Left:
    //     3->0: 3, 2, 1 [endpoint in 0]

    private void touchLine(float stopX, float stopY) {
        float startX = mLastX;
        float startY = mLastY;
        mLastX = stopX;
        mLastY = stopY;
        int startBand = getBarIndex(startX);
        int stopBand = getBarIndex(stopX);
        int direction = stopBand > startBand ? 1 : -1;
        for (int i = startBand; i != stopBand; i += direction) {
            // Get the x-coordinate where we exited band i.
            float exitX = i * mBarWidth;
            if (direction > 0) {
                exitX += mBarWidth;
            }

            // Get the Y value at exitX.
            float slope = (stopY - startY) / (stopX - startX);
            float exitY = startY + slope * (exitX - startX);
            mUiState.setBar(i, yToBar(exitY));
        }
        // Set the Y endpoint.
        mUiState.setBar(stopBand, yToBar(stopY));
    }

    public void onLockStateChange(LockEvent e) {
        // Only spend time redrawing if this is an on/off event.
        if (e == LockEvent.TOGGLE) {
            invalidate();
        }
    }
}
