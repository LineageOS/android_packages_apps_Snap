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
import android.graphics.PorterDuff;
import android.util.AttributeSet;
import android.util.MathUtils;
import android.view.View;

import com.android.camera.CameraManager;

import org.codeaurora.snapcam.R;

public class HistogramView extends View {
    private static final String TAG = "CAM_" + HistogramView.class.getSimpleName();

    private static final int STATS_SIZE = 256;
    private static final int CELL_COUNT = 64;

    private int[] mData = new int[STATS_SIZE + 1];
    private boolean mDataValid;

    private Bitmap mBitmap;
    private Paint mPaint = new Paint();
    private Paint mPaintRect = new Paint();
    private Canvas mCanvas = new Canvas();
    private float mWidth;
    private float mHeight;
    private CameraManager.CameraProxy mGraphCameraDevice;

    public HistogramView(Context context, AttributeSet attrs) {
        super(context, attrs);

        mPaint.setFlags(Paint.ANTI_ALIAS_FLAG);

        setWillNotDraw(false);
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
        mBitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_4444);
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

    public void setOrientation(int orientation) {
        setRotation(-orientation);
        int top = getContext().getResources().getDimensionPixelSize(R.dimen.preview_top_margin);

        if (orientation == 0 || orientation == 180) {
            setTranslationX(0);
            setTranslationY(0);
        } else {
            setTranslationX(mHeight / 2);
            setTranslationY(-(top / 2));
        }
    }

    private void drawGraph() {
        float padding = 10;
        float height = mHeight - (padding * 2);
        float width = mWidth - (padding * 2);
        int cellWidth = Math.round(width / CELL_COUNT);


        mCanvas.drawColor(0, PorterDuff.Mode.CLEAR);

        mPaintRect.setColor(Color.WHITE);

        //Assumption: The first element contains the maximum value.
        int maxValue = Integer.MIN_VALUE;
        if (mData[0] == 0) {
            for (int i = 1; i <= STATS_SIZE; i++) {
                maxValue = Math.max(maxValue, mData[i]);
            }
        } else {
            maxValue = mData[0];
        }

        int[] values = new int[CELL_COUNT];
        int cell = 0;
        float sum = 0.0f;
        for (int i = 1; i < STATS_SIZE; i++) {
            sum += (float) mData[i] / (float) maxValue;
            if ((i % cellWidth) == 0) {
                float mean = sum / cellWidth;
                int value = Math.round(MathUtils.lerp(0, height, mean));
                values[cell] = value;

                float left = padding + (cell * cellWidth);
                float right = left + cellWidth - 2;
                float bottom = mHeight - (padding / 2);
                float top = bottom - value;
                mCanvas.drawRect(left, top, right, bottom, mPaintRect);
                sum = 0.0f;
                cell++;
            }
        }
    }
}

