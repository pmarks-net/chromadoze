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
import android.graphics.Path;
import android.os.Build;
import android.util.AttributeSet;
import android.view.MotionEvent;

import androidx.annotation.NonNull;

public class EqualizerView extends android.view.View implements UIState.LockListener {
    private static final int BAND_COUNT = SpectrumData.BAND_COUNT;

    // 3D projection offsets (multiple of mBarWidth)
    private static final float PROJECT_X = 0.4f;
    private static final float PROJECT_Y = -0.25f;

    // L=light, M=medium, D=dark
    private final Paint mBarColorL[] = new Paint[BAND_COUNT];
    private final Paint mBarColorM[] = new Paint[BAND_COUNT];
    private final Paint mBarColorD[] = new Paint[BAND_COUNT];
    private final Paint mBaseColorL[] = new Paint[4];
    private final Paint mBaseColorM[] = new Paint[4];
    private final Paint mBaseColorD[] = new Paint[4];

    private UIState mUiState;

    private float mWidth;
    private float mHeight;
    private float mBarWidth;
    private float mZeroLineY;

    private Path mCubeTop;
    private Path mCubeSide;

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
            mBarColorL[i] = p;
        }
        darken(0.7f, mBarColorL, mBarColorM);
        darken(0.5f, mBarColorL, mBarColorD);

        int i = 0;
        for (int v : new int[]{100, 75, 55, 50}) {
            Paint p = new Paint();
            p.setColor(Color.rgb(v, v, v));
            mBaseColorL[i++] = p;
        }
        darken(0.7f, mBaseColorL, mBaseColorM);
        darken(0.5f, mBaseColorL, mBaseColorD);
    }

    private void darken(float mult, Paint[] src, Paint[] dst) {
        if (src.length != dst.length) {
            throw new IllegalArgumentException("length mismatch");
        }
        for (int i = 0; i < src.length; i++) {
            int color = src[i].getColor();
            int r = (int) (Color.red(color) * mult);
            int g = (int) (Color.green(color) * mult);
            int b = (int) (Color.blue(color) * mult);
            Paint p = new Paint(src[i]);
            p.setColor(Color.argb(Color.alpha(color), r, g, b));
            dst[i] = p;
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        final Phonon ph = mUiState != null ? mUiState.getPhonon() : null;
        final boolean isLocked = mUiState != null ? mUiState.getLocked() : false;
        final Path p = new Path();

        for (int i = 0; i < BAND_COUNT; i++) {
            float bar = ph != null ? ph.getBar(i) : .5f;
            float startX = bandToX(i);
            float stopX = startX + mBarWidth;
            float startY = barToY(bar);
            float midY = startY + mBarWidth;

            // Lower the brightness and contrast when locked.
            int baseCol = i % 2 + (isLocked ? 2 : 0);

            // Bar right (the top-left corner of this rectangle will be clipped.)
            float projX = mBarWidth * PROJECT_X;
            float projY = mBarWidth * PROJECT_Y;
            canvas.drawRect(stopX, midY + projY,stopX + projX, mHeight, mBaseColorD[baseCol]);

            // Bar front
            canvas.drawRect(startX, midY, stopX, mHeight, mBaseColorL[baseCol]);

            if (bar > 0) {
                // Cube right
                mCubeSide.offset(stopX, startY, p);
                canvas.drawPath(p, mBarColorD[i]);

                // Cube top
                mCubeTop.offset(startX, startY, p);
                canvas.drawPath(p, mBarColorM[i]);

                // Cube front
                canvas.drawRect(startX, startY, stopX, midY, mBarColorL[i]);
            } else {
                // Bar top
                mCubeTop.offset(startX, midY, p);
                canvas.drawPath(p, mBaseColorM[baseCol]);
            }
        }
    }

    private float mLastX;
    private float mLastY;

    @Override
    public boolean onTouchEvent(@NonNull MotionEvent event) {
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

        PhononMutable phm = mUiState.getPhononMutable();
        for (int i = 0; i < event.getHistorySize(); i++) {
            touchLine(phm, event.getHistoricalX(i), event.getHistoricalY(i));
        }
        touchLine(phm, event.getX(), event.getY());

        if (mUiState.sendIfDirty()) {
            invalidate();
        }
        return true;
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        mWidth = getWidth();
        mHeight = getHeight();
        mBarWidth = mWidth / (BAND_COUNT + 2);
        mZeroLineY = mHeight * .9f;
        mCubeTop = projectCube(mBarWidth, true);
        mCubeSide = projectCube(mBarWidth, false);
    }

    // Draw the top or right side of a cube.
    private static Path projectCube(float unit, boolean isTop) {
        float projX = unit * PROJECT_X;
        float projY = unit * PROJECT_Y;
        Path p = new Path();
        p.moveTo(0, 0);
        p.lineTo(projX, projY);
        if (isTop) {
            // Top
            p.lineTo(unit + projX, projY);
            p.lineTo(unit, 0);
        } else {
            // Side
            p.lineTo(projX, unit + projY);
            p.lineTo(0, unit);
        }
        p.close();
        return p;
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

    // Accepts 0 <= barIndex < BAND_COUNT,
    // leaving a 1-bar gap on each side of the screen.
    private float bandToX(int barIndex) {
        return mBarWidth * (barIndex + 1);
    }

    // Returns 0 <= out < BAND_COUNT,
    // leaving a 1-bar gap on each side of the screen.
    private int xToBand(float x) {
        int out = ((int) (x / mBarWidth)) - 1;
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

    private void touchLine(PhononMutable phm, float stopX, float stopY) {
        float startX = mLastX;
        float startY = mLastY;
        mLastX = stopX;
        mLastY = stopY;
        int startBand = xToBand(startX);
        int stopBand = xToBand(stopX);
        int direction = stopBand > startBand ? 1 : -1;
        for (int i = startBand; i != stopBand; i += direction) {
            // Get the x-coordinate where we exited band i.
            float exitX = bandToX(direction < 0 ? i : i + 1);

            // Get the Y value at exitX.
            float slope = (stopY - startY) / (stopX - startX);
            float exitY = startY + slope * (exitX - startX);
            phm.setBar(i, yToBar(exitY));
        }
        // Set the Y endpoint.
        phm.setBar(stopBand, yToBar(stopY));
    }

    @Override
    public void onLockStateChange(LockEvent e) {
        // Only spend time redrawing if this is an on/off event.
        if (e == LockEvent.TOGGLE) {
            invalidate();
        }
    }
}
