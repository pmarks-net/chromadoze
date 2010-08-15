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
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.MotionEvent;

public class EqualizerView extends android.view.View {
    // The current value of each bar, [0.0, 1.0]
    private float mBars[] = new float[SpectrumData.BAND_COUNT];
    
    private Paint mBarColor[] = new Paint[mBars.length];
    private Paint mBaseColor[] = new Paint[2];

    private float mWidth;
    private float mHeight;
    private float mBarWidth;
    private float mZeroLineY;
    
    private boolean mSendEnabled = false;
    
    public EqualizerView(Context context, AttributeSet attrs) {
        super(context, attrs);
        makeColors();
    }
    
    public void saveState(SharedPreferences.Editor pref) {
        for (int i = 0; i < mBars.length; i++) {
            pref.putFloat("barHeight" + i, mBars[i]);
        }
    }
    
    public void loadState(SharedPreferences pref) {
        for (int i = 0; i < mBars.length; i++) {
            mBars[i] = pref.getFloat("barHeight" + i, .5f);
        }
    }
    
    private void makeColors() {
        Bitmap bmp = null;
        try {
            bmp = BitmapFactory.decodeResource(getResources(), R.drawable.spectrum);
        } catch (Resources.NotFoundException e) {
        }
        
        if (bmp != null) {
            for (int i = 0; i < mBars.length; i++) {
                Paint p = new Paint();
                int x = (bmp.getWidth() - 1) * i / (mBars.length - 1);
                p.setColor(bmp.getPixel(x, 0));
                mBarColor[i] = p;
            }
        } else {
            // HACK: The layout editor can't see my resource, so fill in red.
            Paint p = new Paint();
            p.setColor(Color.RED);
            for (int i = 0; i < mBars.length; i++) {
                mBarColor[i] = p;
            }
        }
        
        Paint p = new Paint();
        p.setColor(Color.rgb(100, 100, 100));
        mBaseColor[0] = p;
        p = new Paint();
        p.setColor(Color.rgb(75, 75, 75));
        mBaseColor[1] = p;
    }
    
    @Override
    protected void onDraw(Canvas canvas) {
        for (int i = 0; i < mBars.length; i++) {
            float startX = mBarWidth * i;
            float stopX = startX + mBarWidth;
            float startY = barToY(mBars[i]);
            float midY = startY + mBarWidth;
            
            if (mBars[i] > 0) {
                canvas.drawRect(startX, startY, stopX, midY, mBarColor[i]);
            }
            canvas.drawRect(startX, midY, stopX, mHeight, mBaseColor[i % 2]);
        }
        
        Paint p = new Paint();
        p.setColor(Color.WHITE);
        canvas.drawLine(0, mZeroLineY, mWidth, mZeroLineY, p);
    }
    
    float mLastX;
    float mLastY;
    boolean mSpectrumChanged;
    
    @Override
    public boolean onTouchEvent(MotionEvent event) {
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
        
        mSpectrumChanged = false;
        
        for (int i = 0; i < event.getHistorySize(); i++) {
            touchLine(event.getHistoricalX(i), event.getHistoricalY(i));
        }
        touchLine(event.getX(), event.getY());
        
        if (mSpectrumChanged) {
            sendNewSpectrum();
            invalidate();
        }
        return true;
    }
    
    public void startSending() {
        mSendEnabled = true;
        sendNewSpectrum();
    }
    
    public void stopSending() {
        mSendEnabled = false;
        Intent intent = new Intent(getContext(), NoiseService.class);
        getContext().stopService(intent);
    }
    
    public void setSendEnabled(boolean set) {
        mSendEnabled = set;
    }
    
    private void sendNewSpectrum() {
        if (!mSendEnabled) {
            return;
        }
        Intent intent = new Intent(getContext(), NoiseService.class);
        intent.putExtra("spectrum", new SpectrumData(mBars));
        getContext().startService(intent);
    }
    
    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        mWidth = getWidth();
        mHeight = getHeight();
        mBarWidth = mWidth / mBars.length;
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
        if (out > mBars.length - 1) {
            out = mBars.length - 1;
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
            setBarHeight(i, exitY);
        }
        // Set the Y endpoint.
        setBarHeight(stopBand, stopY);
    }
    
    private void setBarHeight(int band, float y) {
        float barHeight = yToBar(y);
        if (mBars[band] != barHeight) {
            mBars[band] = barHeight;
            mSpectrumChanged = true;
        }
    }
}
