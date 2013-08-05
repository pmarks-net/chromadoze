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
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff.Mode;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.View;

public class EqualizerViewLite extends View {
    public static final int BAND_COUNT = SpectrumData.BAND_COUNT;

    private UIState mUiState;

    private int mWidth;
    private int mHeight;
    private float mBarWidth;

    private Bitmap mBitmap = null;

    public EqualizerViewLite(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public void setUiState(UIState uiState) {
        mUiState = uiState;
        mBitmap = null;
        invalidate();
    }

    private Bitmap makeBitmap() {
        Bitmap bmp = Bitmap.createBitmap(mWidth, mHeight, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bmp);

        // Draw a white line
        Paint whitePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        whitePaint.setColor(Color.WHITE);
        whitePaint.setStyle(Paint.Style.STROKE);
        whitePaint.setStrokeWidth(dpToPixels(3));

        Path path = new Path();
        boolean first = true;
        for (int i = 0; i < BAND_COUNT; i++) {
            float bar = mUiState != null ? mUiState.getBar(i) : .5f;
            float x = mBarWidth * (i + 0.5f);
            float y = barToY(bar);

            if (first) {
                first = false;
                path.moveTo(x, y);
            } else {
                path.lineTo(x, y);
            }
        }
        canvas.drawPath(path, whitePaint);

        // Overlay the spectrum bitmap to add color.
        Bitmap colorBmp = BitmapFactory.decodeResource(getResources(), R.drawable.spectrum);
        Rect src = new Rect(0, 0, colorBmp.getWidth(), colorBmp.getHeight());
        Rect dst = new Rect(0, 0, bmp.getWidth(), bmp.getHeight());
        Paint alphaPaint = new Paint();
        alphaPaint.setXfermode(new PorterDuffXfermode(Mode.SRC_IN));
        canvas.drawBitmap(colorBmp, src, dst, alphaPaint);

        return bmp;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (mBitmap == null) {
            mBitmap = makeBitmap();
        }
        canvas.drawBitmap(mBitmap, 0, 0, null);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        mWidth = getWidth();
        mHeight = getHeight();
        mBarWidth = (float)mWidth / BAND_COUNT;
        mBitmap = null;
    }

    private float barToY(float barHeight) {
        return (1f - barHeight) * mHeight;
    }

    private float dpToPixels(float dp) {
        Resources r = getResources();
        return TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, dp, r.getDisplayMetrics());
    }
}
