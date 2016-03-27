/*
 * Copyright (C) 2013 The Android Open Source Project
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

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Rect;
import android.hardware.display.DisplayManager;
import android.hardware.display.DisplayManager.DisplayListener;
import android.util.AttributeSet;
import android.view.View;
import android.widget.FrameLayout;

import com.android.camera.util.ApiHelper;

@SuppressLint("NewApi")
public class CameraRootView extends FrameLayout {

    private int mTopMargin = 0;
    private int mBottomMargin = 0;
    private int mLeftMargin = 0;
    private int mRightMargin = 0;
    private Object mDisplayListener;
    private MyDisplayListener mListener;
    private Rect mLastInsets = new Rect();
    private Rect mTmpRect = new Rect();

    public interface MyDisplayListener {
        public void onDisplayChanged();
    }

    public CameraRootView(Context context, AttributeSet attrs) {
        super(context, attrs);
        initDisplayListener();
    }

    public void redoFitSystemWindows() {
        if (mLastInsets.left != 0 || mLastInsets.right != 0
                || mLastInsets.top != 0 || mLastInsets.bottom != 0) {
            Rect insets = new Rect(mLastInsets);
            fitSystemWindows(insets);
        }
    }

    @Override
    protected boolean fitSystemWindows(Rect insets) {
        mLastInsets.set(insets);
        return super.fitSystemWindows(insets);
    }

    public void initDisplayListener() {
        if (ApiHelper.HAS_DISPLAY_LISTENER) {
            mDisplayListener = new DisplayListener() {

                @Override
                public void onDisplayAdded(int arg0) {}

                @Override
                public void onDisplayChanged(int arg0) {
                    if (mListener != null) {
                        mListener.onDisplayChanged();
                    }
                }

                @Override
                public void onDisplayRemoved(int arg0) {}
            };
        }
    }

    public Rect getInsetsForOrientation(int orientation) {
        switch (orientation) {
            case 90:
                mTmpRect.set(mLastInsets.top, mLastInsets.right,
                        mLastInsets.bottom, mLastInsets.left);
                break;
            case 180:
                mTmpRect.set(mLastInsets.right, mLastInsets.bottom,
                        mLastInsets.left, mLastInsets.top);
                break;
            case 270:
                mTmpRect.set(mLastInsets.bottom, mLastInsets.left,
                        mLastInsets.top, mLastInsets.right);
                break;
            default:
                mTmpRect.set(mLastInsets);
                break;
        }

        return mTmpRect;
    }

    public Rect getClientRectForOrientation(int orientation) {
        Rect result = getInsetsForOrientation(orientation);
        if (orientation == 90 || orientation == 270) {
            result.right = getHeight() - result.right;
            result.bottom = getWidth() - result.bottom;
        } else {
            result.right = getWidth() - result.right;
            result.bottom = getHeight() - result.bottom;
        }
        return result;
    }

    public void removeDisplayChangeListener() {
        mListener = null;
    }

    public void setDisplayChangeListener(MyDisplayListener listener) {
        mListener = listener;
    }

    @Override
    public void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (ApiHelper.HAS_DISPLAY_LISTENER) {
            ((DisplayManager) getContext().getSystemService(Context.DISPLAY_SERVICE))
            .registerDisplayListener((DisplayListener) mDisplayListener, null);
        }
    }

    @Override
    public void onDetachedFromWindow () {
        super.onDetachedFromWindow();
        if (ApiHelper.HAS_DISPLAY_LISTENER) {
            ((DisplayManager) getContext().getSystemService(Context.DISPLAY_SERVICE))
            .unregisterDisplayListener((DisplayListener) mDisplayListener);
        }
    }
}
