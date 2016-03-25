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
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

import com.android.camera.CameraManager;
import org.codeaurora.snapcam.R;

public class HistogramView extends View {
    private static final int STATS_SIZE = 256;
    private static final int GRID_LINES = 10;

    private float[] mData = new float[STATS_SIZE];
    private int mMaxDataValue;
    private boolean mDataValid;

    private int mBgColor;
    private int mBarColor;
    private int mGridColor;

    private Paint mPaint = new Paint();
    private CameraManager.CameraProxy mGraphCameraDevice;

    public HistogramView(Context context, AttributeSet attrs) {
        super(context,attrs);

        Resources res = context.getResources();
        mBgColor = res.getColor(R.color.camera_control_bg_transparent);
        mBarColor = res.getColor(R.color.histogram_bars);
        mGridColor = res.getColor(R.color.histogram_grid_lines);

        mPaint.setAntiAlias(true);
        mPaint.setStyle(Paint.Style.FILL);
    }

    public void setCamera(CameraManager.CameraProxy camera) {
        mGraphCameraDevice = camera;
        if (camera == null) {
            mDataValid = false;
        }
    }

    public void updateData(int[] data) {
        if (data.length != STATS_SIZE + 1) {
            return;
        }

        int maxValue;
        //Assumption: The first element contains the maximum value.
        if (data[0] == 0) {
            maxValue = Integer.MIN_VALUE;
            for (int i = 1; i <= STATS_SIZE ; i++) {
                maxValue = Math.max(maxValue, data[i]);
            }
        } else {
            maxValue = data[0];
        }

        // mData = data scaled by maxValue
        for (int i = 0; i < STATS_SIZE; i++) {
            mData[i] = Math.min((float) data[i + 1] / (float) maxValue, 1.0F);
        }
        mDataValid = true;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (!mDataValid) {
            return;
        }

        final int paddingLeft = getPaddingLeft();
        final int paddingRight = getPaddingRight();
        final int paddingTop = getPaddingTop();
        final int paddingBottom = getPaddingBottom();

        float graphWidth = getWidth() - paddingLeft - paddingRight;
        float graphHeight = getHeight() - paddingTop - paddingBottom;
        float barWidth = graphWidth / STATS_SIZE;

        mPaint.setColor(mBgColor);
        canvas.drawRect(0, 0, getWidth(), getHeight(), mPaint);

        mPaint.setColor(mGridColor);
        for (int row = 0; row <= GRID_LINES; row++) {
            float y = (graphHeight * row / GRID_LINES) + paddingTop;
            canvas.drawLine(paddingLeft, y, graphWidth + paddingLeft, y, mPaint);
        }
        for (int col = 0; col <= GRID_LINES; col++) {
            float x = (graphWidth * col / GRID_LINES) + paddingLeft;
            canvas.drawLine(x, paddingTop, x, graphHeight + paddingTop, mPaint);
        }

        mPaint.setColor(mBarColor);
        for (int i = 0; i < STATS_SIZE; i++)  {
            float barHeight = mData[i] * graphHeight;
            float left = (barWidth * i) + paddingLeft;
            float right = left + barWidth;
            float bottom = graphHeight + paddingTop;
            float top = bottom - barHeight;
            canvas.drawRect(left, top, right, bottom, mPaint);
        }

        if (mGraphCameraDevice != null) {
            mGraphCameraDevice.sendHistogramData();
        }
    }
}

