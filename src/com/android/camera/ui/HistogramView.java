/*
 * Copyright (C) 2012 The Android Open Source Project
 * Copyright (C) 2013-2015 The CyanogenMod Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.camera.ui;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

import com.android.camera.CameraManager;

public class HistogramView extends View {
    private static final int STATS_SIZE = 256;

    private int[] mData = new int[STATS_SIZE + 1];
    private boolean mDataValid;

    private Bitmap  mBitmap;
    private Paint   mPaint = new Paint();
    private Paint   mPaintRect = new Paint();
    private Canvas  mCanvas = new Canvas();
    private float   mWidth;
    private float   mHeight;
    private CameraManager.CameraProxy mGraphCameraDevice;

    public HistogramView(Context context, AttributeSet attrs) {
        super(context,attrs);

        mPaint.setFlags(Paint.ANTI_ALIAS_FLAG);
        mPaintRect.setColor(0xFFFFFFFF);
        mPaintRect.setStyle(Paint.Style.FILL);
    }

    public void setCamera(CameraManager.CameraProxy camera) {
        mGraphCameraDevice = camera;
        if (camera == null) {
            mDataValid = false;
        }
    }

    public void updateData(int[] data) {
        if (data.length == mData.length) {
            System.arraycopy(data, 0, mData, 0, data.length);
            drawGraph();
            mDataValid = true;
            invalidate();
        }
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        mBitmap = Bitmap.createBitmap(w, h, Bitmap.Config.RGB_565);
        mCanvas.setBitmap(mBitmap);
        mWidth = w;
        mHeight = h;
        if (mDataValid) {
            drawGraph();
        }
        super.onSizeChanged(w, h, oldw, oldh);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (mDataValid && mBitmap != null) {
            canvas.drawBitmap(mBitmap, 0, 0, null);
            if (mGraphCameraDevice != null) {
                mGraphCameraDevice.sendHistogramData();
            }
        }
    }

    private void drawGraph() {
        final float border = 5;
        float graphheight = mHeight - (2 * border);
        float graphwidth = mWidth - (2 * border);
        float bargap = 0.0f;
        float barwidth = graphwidth/STATS_SIZE;

        mCanvas.drawColor(0xFFAAAAAA);
        mPaint.setColor(Color.BLACK);

        for (int k = 0; k <= (graphheight /32) ; k++) {
            float y = (float)(32 * k)+ border;
            mCanvas.drawLine(border, y, graphwidth + border , y, mPaint);
        }
        for (int j = 0; j <= (graphwidth /32); j++) {
            float x = (float)(32 * j)+ border;
            mCanvas.drawLine(x, border, x, graphheight + border, mPaint);
        }

        //Assumption: The first element contains the maximum value.
        int maxValue = Integer.MIN_VALUE;
        if (mData[0] == 0) {
            for (int i = 1; i <= STATS_SIZE ; i++) {
                maxValue = Math.max(maxValue, mData[i]);
            }
        } else {
            maxValue = mData[0];
        }

        for (int i = 1; i <= STATS_SIZE; i++)  {
            float scaled = Math.min(STATS_SIZE,
                    (float) mData[i] * (float) STATS_SIZE / (float) maxValue);
            float left = (bargap * (i+1)) + (barwidth * i) + border;
            float top = graphheight + border;
            float right = left + barwidth;
            float bottom = top - scaled;
            mCanvas.drawRect(left, top, right, bottom, mPaintRect);
        }
    }
}

