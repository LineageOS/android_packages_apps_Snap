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

import android.animation.Animator;
import android.animation.Animator.AnimatorListener;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.AnimationDrawable;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.AttributeSet;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewPropertyAnimator;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.ArrayList;

import org.codeaurora.snapcam.R;
import com.android.camera.ui.ModuleSwitcher;
import com.android.camera.ui.RotateImageView;
import com.android.camera.ShutterButton;
import com.android.camera.Storage;
import com.android.camera.util.CameraUtil;
import com.android.camera.TsMakeupManager;

public class CameraControls extends RotatableLayout {

    private static final String TAG = "CAM_Controls";

    private View mBackgroundView;
    private View mShutter;
    private View mVideoShutter;
    private View mSwitcher;
    private View mExitPanorama;
    private View mMenu;
    private View mMute;
    private View mFrontBackSwitcher;
    private View mHdrSwitcher;
    private View mTsMakeupSwitcher;
    private View mBokehSwitcher;
    private View mPreview;
    private View mSceneModeSwitcher;
    private View mFilterModeSwitcher;
    private View mReviewDoneButton;
    private View mReviewCancelButton;
    private View mReviewRetakeButton;
    private ArrowTextView mRefocusToast;

    private int mSize;
    private static int WIDTH_GRID = 5;
    private static final int HEIGHT_GRID = 7;
    private static boolean isAnimating = false;
    private ArrayList<View> mViewList;
    private static final int FRONT_BACK_INDEX = 0;
    private static final int HDR_INDEX = 1;
    private static final int TS_MAKEUP_INDEX = 1;
    private static final int SCENE_MODE_INDEX = 2;
    private static final int FILTER_MODE_INDEX = 3;
    private static final int MENU_INDEX = 4;
    private static final int SWITCHER_INDEX = 5;
    private static final int SHUTTER_INDEX = 6;
    private static final int PREVIEW_INDEX = 7;
    private static final int INDICATOR_INDEX = 8;
    private static final int MUTE_INDEX = 9;
    private static final int VIDEO_SHUTTER_INDEX = 10;
    private static final int EXIT_PANORAMA_INDEX = 11;
    private static final int BOKEH_INDEX = 12;
    private static final int MAX_INDEX= 13;
    private static final int ANIME_DURATION = 300;
    private float[][] mLocX = new float[4][MAX_INDEX];
    private float[][] mLocY = new float[4][MAX_INDEX];
    private boolean mLocSet = false;
    private boolean mHideRemainingPhoto = false;
    private LinearLayout mRemainingPhotos;
    private TextView mRemainingPhotosText;
    private int mCurrentRemaining = -1;
    private int mOrientation;

    private int mPreviewRatio;
    private static int mTopMargin = 0;
    private static int mBottomMargin = 0;

    private Paint mPaint;

    private static final int LOW_REMAINING_PHOTOS = 20;
    private static final int HIGH_REMAINING_PHOTOS = 1000000;

    private boolean mIsBokehMode = false;

    AnimatorListener outlistener = new AnimatorListener() {
        @Override
        public void onAnimationStart(Animator animation) {
        }

        @Override
        public void onAnimationRepeat(Animator animation) {

        }

        @Override
        public void onAnimationEnd(Animator animation) {
            resetLocation(0, 0);

            mFrontBackSwitcher.setVisibility(View.INVISIBLE);
            if(TsMakeupManager.HAS_TS_MAKEUP) {
                mTsMakeupSwitcher.setVisibility(View.INVISIBLE);
            } else {
                mHdrSwitcher.setVisibility(View.INVISIBLE);
            }
            if (mIsBokehMode) {
                mBokehSwitcher.setVisibility(View.INVISIBLE);
            }
            mSceneModeSwitcher.setVisibility(View.INVISIBLE);
            mFilterModeSwitcher.setVisibility(View.INVISIBLE);
            mSwitcher.setVisibility(View.INVISIBLE);
            mShutter.setVisibility(View.INVISIBLE);
            mVideoShutter.setVisibility(View.INVISIBLE);
            mMenu.setVisibility(View.INVISIBLE);
            mMute.setVisibility(View.INVISIBLE);
            mExitPanorama.setVisibility(View.INVISIBLE);
            mPreview.setVisibility(View.INVISIBLE);
            isAnimating = false;
            enableTouch(true);
        }

        @Override
        public void onAnimationCancel(Animator animation) {
            resetLocation(0, 0);

            mFrontBackSwitcher.setVisibility(View.INVISIBLE);
            if(TsMakeupManager.HAS_TS_MAKEUP) {
                mTsMakeupSwitcher.setVisibility(View.INVISIBLE);
            } else {
                mHdrSwitcher.setVisibility(View.INVISIBLE);
            }
            if (mIsBokehMode) {
                mBokehSwitcher.setVisibility(View.INVISIBLE);
            }
            mSceneModeSwitcher.setVisibility(View.INVISIBLE);
            mFilterModeSwitcher.setVisibility(View.INVISIBLE);
            mSwitcher.setVisibility(View.INVISIBLE);
            mShutter.setVisibility(View.INVISIBLE);
            mVideoShutter.setVisibility(View.INVISIBLE);
            mMenu.setVisibility(View.INVISIBLE);
            mMute.setVisibility(View.INVISIBLE);
            mExitPanorama.setVisibility(View.INVISIBLE);
            mPreview.setVisibility(View.INVISIBLE);
            isAnimating = false;
            enableTouch(true);
        }
    };

    public View getPanoramaExitButton() {
        return mExitPanorama;
    }

    AnimatorListener inlistener = new AnimatorListener() {
        @Override
        public void onAnimationStart(Animator animation) {
        }

        @Override
        public void onAnimationRepeat(Animator animation) {

        }

        @Override
        public void onAnimationEnd(Animator animation) {
            isAnimating = false;
            resetLocation(0, 0);
            enableTouch(true);
        }

        @Override
        public void onAnimationCancel(Animator animation) {
            isAnimating = false;
            resetLocation(0, 0);
            enableTouch(true);
        }
    };

    public CameraControls(Context context, AttributeSet attrs) {
        super(context, attrs);

        mPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        setWillNotDraw(false);

        mRefocusToast = new ArrowTextView(context);
        addView(mRefocusToast);
        setClipChildren(false);

        setMeasureAllChildren(true);
    }

    public CameraControls(Context context) {
        this(context, null);
    }

    public static boolean isAnimating() {
        return isAnimating;
    }

    public void enableTouch(boolean enable) {
        if (enable) {
            ((ShutterButton) mShutter).setPressed(false);
            mVideoShutter.setPressed(false);
            mSwitcher.setPressed(false);
            mMenu.setPressed(false);
            mMute.setPressed(false);
            mExitPanorama.setPressed(false);
            mFrontBackSwitcher.setPressed(false);
            if(TsMakeupManager.HAS_TS_MAKEUP) {
                mTsMakeupSwitcher.setPressed(false);
            } else {
                mHdrSwitcher.setPressed(false);
            }
            if (mIsBokehMode) {
                mBokehSwitcher.setPressed(false);
            }
            mSceneModeSwitcher.setPressed(false);
        }


        ((ShutterButton) mShutter).enableTouch(enable);
        mVideoShutter.setClickable(enable);
        ((ModuleSwitcher) mSwitcher).enableTouch(enable);
        mMenu.setEnabled(enable);
        mMute.setEnabled(enable);
        mExitPanorama.setEnabled(enable);
        mFrontBackSwitcher.setEnabled(enable);
        if(TsMakeupManager.HAS_TS_MAKEUP) {
            mTsMakeupSwitcher.setEnabled(enable);
        } else {
            mHdrSwitcher.setEnabled(enable && !mIsBokehMode);
        }
        if (mIsBokehMode) {
            mBokehSwitcher.setEnabled(enable);
        }
        mPreview.setEnabled(enable);

    }

    private void markVisibility() {
        mViewList = new ArrayList<View>();
        if (mFrontBackSwitcher.getVisibility() == View.VISIBLE)
            mViewList.add(mFrontBackSwitcher);
        if(TsMakeupManager.HAS_TS_MAKEUP) {
            if (mTsMakeupSwitcher.getVisibility() == View.VISIBLE)
                mViewList.add(mTsMakeupSwitcher);
        } else {
            if (mHdrSwitcher.getVisibility() == View.VISIBLE)
                mViewList.add(mHdrSwitcher);
        }
        if (mIsBokehMode) {
            if (mBokehSwitcher.getVisibility() == View.VISIBLE) {
                mViewList.add(mBokehSwitcher);
            }
        }
        if (mSceneModeSwitcher.getVisibility() == View.VISIBLE)
            mViewList.add(mSceneModeSwitcher);
        if (mFilterModeSwitcher.getVisibility() == View.VISIBLE)
            mViewList.add(mFilterModeSwitcher);
        if (mShutter.getVisibility() == View.VISIBLE)
            mViewList.add(mShutter);
        if (mVideoShutter.getVisibility() == View.VISIBLE)
            mViewList.add(mVideoShutter);
        if (mMenu.getVisibility() == View.VISIBLE)
            mViewList.add(mMenu);
        if (mMute.getVisibility() == View.VISIBLE)
            mViewList.add(mMute);
        if (mExitPanorama.getVisibility() == View.VISIBLE)
            mViewList.add(mExitPanorama);
    }

    public void removeFromViewList(View view) {
        if(mViewList != null) {
            mViewList.remove(view);
        }
    }

    @Override
    public void onFinishInflate() {
        super.onFinishInflate();
        mBackgroundView = findViewById(R.id.blocker);
        mSwitcher = findViewById(R.id.camera_switcher);
        mShutter = findViewById(R.id.shutter_button);
        mVideoShutter = findViewById(R.id.video_button);
        mFrontBackSwitcher = findViewById(R.id.front_back_switcher);
        if(TsMakeupManager.HAS_TS_MAKEUP) {
            mTsMakeupSwitcher = findViewById(R.id.ts_makeup_switcher);
        } else {
            mHdrSwitcher = findViewById(R.id.hdr_switcher);
        }
        mBokehSwitcher = findViewById(R.id.bokeh_switcher);
        mMenu = findViewById(R.id.menu);
        mMute = findViewById(R.id.mute_button);
        mExitPanorama = findViewById(R.id.exit_panorama);
        mExitPanorama.setVisibility(View.GONE);
        mPreview = findViewById(R.id.preview_thumb);
        mSceneModeSwitcher = findViewById(R.id.scene_mode_switcher);
        mFilterModeSwitcher = findViewById(R.id.filter_mode_switcher);
        mRemainingPhotos = (LinearLayout) findViewById(R.id.remaining_photos);
        mRemainingPhotosText = (TextView) findViewById(R.id.remaining_photos_text);
    }

    @Override
    public void onLayout(boolean changed, int l, int t, int r, int b) {
        int orientation = getResources().getConfiguration().orientation;
        int size = getResources().getDimensionPixelSize(R.dimen.camera_controls_size);
        int rotation = getUnifiedRotation();
        adjustBackground();
        // As l,t,r,b are positions relative to parents, we need to convert them
        // to child's coordinates
        r = r - l;
        b = b - t;
        l = 0;
        t = 0;
        for (int i = 0; i < getChildCount(); i++) {
            View v = getChildAt(i);
            v.layout(l, t, r, b);
        }
        Rect shutter = new Rect();
        center(mShutter, l, t, r, b, orientation, rotation, shutter, SHUTTER_INDEX);
        mSize = (int) (Math.max(shutter.right - shutter.left, shutter.bottom - shutter.top) * 1.2f);
        center(mBackgroundView, l, t, r, b, orientation, rotation, new Rect(), -1);
        mBackgroundView.setVisibility(View.GONE);
        setLocation(r - l, b - t);

        View retake = findViewById(R.id.btn_retake);
        if (retake != null) {
            mReviewRetakeButton = retake;
            mReviewCancelButton = findViewById(R.id.btn_cancel);
            mReviewDoneButton = findViewById(R.id.btn_done);

            center(mReviewRetakeButton, shutter, rotation);
            toLeft(mReviewCancelButton, shutter, rotation);
            toRight(mReviewDoneButton, shutter, rotation);
        } else {
            mReviewRetakeButton = null;
            mReviewCancelButton = null;
            mReviewDoneButton = null;
        }
        layoutRemaingPhotos();
        mRemainingPhotos.setVisibility(View.VISIBLE);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (mTopMargin != 0) {
            int rotation = getUnifiedRotation();
            int w = canvas.getWidth(), h = canvas.getHeight();
            switch (rotation) {
                case 90:
                    canvas.drawRect(0, 0, mTopMargin, h, mPaint);
                    canvas.drawRect(w - mBottomMargin, 0, w, h, mPaint);
                    break;
                case 180:
                    canvas.drawRect(0, 0, w, mBottomMargin, mPaint);
                    canvas.drawRect(0, h - mTopMargin, w, h, mPaint);
                    break;
                case 270:
                    canvas.drawRect(0, 0, mBottomMargin, h, mPaint);
                    canvas.drawRect(w - mTopMargin, 0, w, h, mPaint);
                    break;
                default:
                    canvas.drawRect(0, 0, w, mTopMargin, mPaint);
                    canvas.drawRect(0, h - mBottomMargin, w, h, mPaint);
                    break;
            }
        }
    }

    private void setLocation(int w, int h) {
        int rotation = getUnifiedRotation();
        if (mIsBokehMode) {
            toIndex(mSwitcher, w, h, rotation, 5, 6, SWITCHER_INDEX);
            toIndex(mBokehSwitcher,w,h,rotation,5,0,BOKEH_INDEX);
        } else {
            toIndex(mSwitcher, w, h, rotation, 4, 6, SWITCHER_INDEX);
        }
        toIndex(mVideoShutter, w, h, rotation, 3, 6, VIDEO_SHUTTER_INDEX);
        toIndex(mSceneModeSwitcher, w, h, rotation, 4, 0, SCENE_MODE_INDEX);
        toIndex(mMute, w, h, rotation, 4, 0, MUTE_INDEX);
        toIndex(mFilterModeSwitcher, w, h, rotation, 3, 0, FILTER_MODE_INDEX);
        toIndex(mExitPanorama, w, h, rotation, 0, 0, EXIT_PANORAMA_INDEX);
        toIndex(mFrontBackSwitcher, w, h, rotation, 1, 0, FRONT_BACK_INDEX);
        toIndex(mPreview, w, h, rotation, 0, 6, PREVIEW_INDEX);
        if(TsMakeupManager.HAS_TS_MAKEUP) {
            toIndex(mTsMakeupSwitcher, w, h, rotation, 2, 0, TS_MAKEUP_INDEX);
        } else {
            toIndex(mHdrSwitcher, w, h, rotation, 2, 0, HDR_INDEX);
        }
        toIndex(mMenu, w, h, rotation, 0, 0, MENU_INDEX);
        layoutToast(mRefocusToast, w, h, rotation);
    }

    private void layoutToast(final View v, int w, int h, int rotation) {
        int tw = v.getMeasuredWidth();
        int th = v.getMeasuredHeight();
        int l, t, r, b, c;
        switch (rotation) {
            case 90:
                c = (int) (h / WIDTH_GRID * (WIDTH_GRID - 0.5));
                t = c - th / 2;
                b = c + th / 2;
                r = (int) (w / HEIGHT_GRID * (HEIGHT_GRID - 1.25));
                l = r - tw;
                mRefocusToast.setArrow(tw, th / 2, tw + th / 2, th, tw, th);
                break;
            case 180:
                t = (int) (h / HEIGHT_GRID * 1.25);
                b = t + th;
                r = (int) (w / WIDTH_GRID * (WIDTH_GRID - 0.25));
                l = r - tw;
                mRefocusToast.setArrow(tw - th / 2, 0, tw, 0, tw, - th / 2);
                break;
            case 270:
                c = (int) (h / WIDTH_GRID * 0.5);
                t = c - th / 2;
                b = c + th / 2;
                l = (int) (w / HEIGHT_GRID * 1.25);
                r = l + tw;
                mRefocusToast.setArrow(0, 0, 0, th / 2, - th / 2, 0);
                break;
            default:
                l = w / WIDTH_GRID / 4;
                b = (int) (h / HEIGHT_GRID * (HEIGHT_GRID - 1.25));
                r = l + tw;
                t = b - th;
                mRefocusToast.setArrow(0, th, th / 2, th, 0, th * 3 / 2);
                break;
        }
        mRefocusToast.layout(l, t, r, b);
    }

    private void center(View v, int l, int t, int r, int b, int orientation, int rotation,
            Rect result, int idx) {
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) v.getLayoutParams();
        int tw = lp.leftMargin + v.getMeasuredWidth() + lp.rightMargin;
        int th = lp.topMargin + v.getMeasuredHeight() + lp.bottomMargin;
        switch (rotation) {
            case 0:
                // phone portrait; controls bottom
                result.left = (r + l) / 2 - tw / 2 + lp.leftMargin;
                result.right = (r + l) / 2 + tw / 2 - lp.rightMargin;
                result.bottom = b - lp.bottomMargin;
                result.top = b - th + lp.topMargin;
                break;
            case 90:
                // phone landscape: controls right
                result.right = r - lp.rightMargin;
                result.left = r - tw + lp.leftMargin;
                result.top = (b + t) / 2 - th / 2 + lp.topMargin;
                result.bottom = (b + t) / 2 + th / 2 - lp.bottomMargin;
                break;
            case 180:
                // phone upside down: controls top
                result.left = (r + l) / 2 - tw / 2 + lp.leftMargin;
                result.right = (r + l) / 2 + tw / 2 - lp.rightMargin;
                result.top = t + lp.topMargin;
                result.bottom = t + th - lp.bottomMargin;
                break;
            case 270:
                // reverse landscape: controls left
                result.left = l + lp.leftMargin;
                result.right = l + tw - lp.rightMargin;
                result.top = (b + t) / 2 - th / 2 + lp.topMargin;
                result.bottom = (b + t) / 2 + th / 2 - lp.bottomMargin;
                break;
        }
        v.layout(result.left, result.top, result.right, result.bottom);
        if (idx != -1) {
            int idx1 = rotation / 90;
            int idx2 = idx;
            mLocX[idx1][idx2] = result.left;
            mLocY[idx1][idx2] = result.top;
        }
    }

    private void resetLocation(float x, float y) {
        int rotation = getUnifiedRotation();
        int idx1 = rotation / 90;

        mFrontBackSwitcher.setX(mLocX[idx1][FRONT_BACK_INDEX] + x);
        if(TsMakeupManager.HAS_TS_MAKEUP) {
            mTsMakeupSwitcher.setX(mLocX[idx1][TS_MAKEUP_INDEX] + x);
        } else {
            mHdrSwitcher.setX(mLocX[idx1][HDR_INDEX] + x);
        }
        if (mIsBokehMode) {
            mBokehSwitcher.setX(mLocX[idx1][BOKEH_INDEX] + x);
        }
        mSceneModeSwitcher.setX(mLocX[idx1][SCENE_MODE_INDEX] + x);
        mFilterModeSwitcher.setX(mLocX[idx1][FILTER_MODE_INDEX] + x);
        mMenu.setX(mLocX[idx1][MENU_INDEX] + x);
        mMute.setX(mLocX[idx1][MUTE_INDEX] + x);
        mExitPanorama.setX(mLocX[idx1][EXIT_PANORAMA_INDEX] + x);
        mSwitcher.setX(mLocX[idx1][SWITCHER_INDEX] - x);
        mShutter.setX(mLocX[idx1][SHUTTER_INDEX] - x);
        mVideoShutter.setX(mLocX[idx1][VIDEO_SHUTTER_INDEX] - x);
        mPreview.setX(mLocX[idx1][PREVIEW_INDEX] - x);

        mFrontBackSwitcher.setY(mLocY[idx1][FRONT_BACK_INDEX] + y);
        if(TsMakeupManager.HAS_TS_MAKEUP) {
            mTsMakeupSwitcher.setY(mLocY[idx1][TS_MAKEUP_INDEX] + y);
        } else {
            mHdrSwitcher.setY(mLocY[idx1][HDR_INDEX] + y);
        }
        if (mIsBokehMode){
            mBokehSwitcher.setY(mLocY[idx1][BOKEH_INDEX] + y);
        }
        mSceneModeSwitcher.setY(mLocY[idx1][SCENE_MODE_INDEX] + y);
        mFilterModeSwitcher.setY(mLocY[idx1][FILTER_MODE_INDEX] + y);
        mMenu.setY(mLocY[idx1][MENU_INDEX] + y);
        mMute.setY(mLocY[idx1][MUTE_INDEX] + y);
        mExitPanorama.setY(mLocY[idx1][EXIT_PANORAMA_INDEX] + y);
        mSwitcher.setY(mLocY[idx1][SWITCHER_INDEX] - y);
        mShutter.setY(mLocY[idx1][SHUTTER_INDEX] - y);
        mVideoShutter.setY(mLocY[idx1][VIDEO_SHUTTER_INDEX] - y);
        mPreview.setY(mLocY[idx1][PREVIEW_INDEX] - y);
    }

    public void setTitleBarVisibility(int status){
        mFrontBackSwitcher.setVisibility(status);
        mMenu.setVisibility(status);
        mSceneModeSwitcher.setVisibility(status);
        mFilterModeSwitcher.setVisibility(status);
        if(TsMakeupManager.HAS_TS_MAKEUP) {
            mTsMakeupSwitcher.setVisibility(status);
        } else {
            mHdrSwitcher.setVisibility(status);
        }
        if (mIsBokehMode) {
            mBokehSwitcher.setVisibility(status);
        }
    }

    public void setBokehMode(boolean enable) {
        mIsBokehMode = enable;
        if (mIsBokehMode) {
            WIDTH_GRID = 6;
        } else {
            WIDTH_GRID = 5;
            mBokehSwitcher.setVisibility(GONE);
        }
        requestLayout();
    }

    public void hideUI() {
        if(!isAnimating)
            enableTouch(false);
        isAnimating = true;
        int rotation = getUnifiedRotation();
        mFrontBackSwitcher.animate().cancel();
        if(TsMakeupManager.HAS_TS_MAKEUP) {
            mTsMakeupSwitcher.animate().cancel();
        } else {
            mHdrSwitcher.animate().cancel();
        }
        if (mIsBokehMode) {
            mBokehSwitcher.animate().cancel();
        }
        mSceneModeSwitcher.animate().cancel();
        mFilterModeSwitcher.animate().cancel();
        mSwitcher.animate().cancel();
        mShutter.animate().cancel();
        mVideoShutter.animate().cancel();
        mMenu.animate().cancel();
        mMute.animate().cancel();
        mExitPanorama.animate().cancel();
        mPreview.animate().cancel();
        mFrontBackSwitcher.animate().setListener(outlistener);
        ((ModuleSwitcher) mSwitcher).removePopup();
        resetLocation(0, 0);
        markVisibility();
        switch (rotation) {
            case 0:
                mFrontBackSwitcher.animate().translationYBy(-mSize).setDuration(ANIME_DURATION);
                if(TsMakeupManager.HAS_TS_MAKEUP) {
                    mTsMakeupSwitcher.animate().translationYBy(-mSize).setDuration(ANIME_DURATION);
                } else {
                    mHdrSwitcher.animate().translationYBy(-mSize).setDuration(ANIME_DURATION);
                }
                if (mIsBokehMode) {
                    mBokehSwitcher.animate().translationYBy(-mSize).setDuration(ANIME_DURATION);
                }
                mSceneModeSwitcher.animate().translationYBy(-mSize).setDuration(ANIME_DURATION);
                mFilterModeSwitcher.animate().translationYBy(-mSize).setDuration(ANIME_DURATION);
                mMenu.animate().translationYBy(-mSize).setDuration(ANIME_DURATION);
                mMute.animate().translationYBy(-mSize).setDuration(ANIME_DURATION);
                mExitPanorama.animate().translationYBy(-mSize).setDuration(ANIME_DURATION);

                mSwitcher.animate().translationYBy(mSize).setDuration(ANIME_DURATION);
                mShutter.animate().translationYBy(mSize).setDuration(ANIME_DURATION);
                mVideoShutter.animate().translationYBy(mSize).setDuration(ANIME_DURATION);
                mPreview.animate().translationYBy(mSize).setDuration(ANIME_DURATION);
                break;
            case 90:
                mFrontBackSwitcher.animate().translationXBy(-mSize).setDuration(ANIME_DURATION);
                if(TsMakeupManager.HAS_TS_MAKEUP) {
                    mTsMakeupSwitcher.animate().translationXBy(-mSize).setDuration(ANIME_DURATION);
                } else {
                    mHdrSwitcher.animate().translationXBy(-mSize).setDuration(ANIME_DURATION);
                }
                if (mIsBokehMode) {
                    mBokehSwitcher.animate().translationXBy(-mSize).setDuration(ANIME_DURATION);
                }
                mSceneModeSwitcher.animate().translationXBy(-mSize).setDuration(ANIME_DURATION);
                mFilterModeSwitcher.animate().translationXBy(-mSize).setDuration(ANIME_DURATION);
                mMenu.animate().translationXBy(-mSize).setDuration(ANIME_DURATION);
                mMute.animate().translationXBy(-mSize).setDuration(ANIME_DURATION);
                mExitPanorama.animate().translationXBy(-mSize).setDuration(ANIME_DURATION);

                mSwitcher.animate().translationXBy(mSize).setDuration(ANIME_DURATION);
                mShutter.animate().translationXBy(mSize).setDuration(ANIME_DURATION);
                mVideoShutter.animate().translationXBy(mSize).setDuration(ANIME_DURATION);
                mPreview.animate().translationXBy(mSize).setDuration(ANIME_DURATION);
                break;
            case 180:
                mFrontBackSwitcher.animate().translationYBy(mSize).setDuration(ANIME_DURATION);
                if(TsMakeupManager.HAS_TS_MAKEUP) {
                    mTsMakeupSwitcher.animate().translationYBy(mSize).setDuration(ANIME_DURATION);
                } else {
                    mHdrSwitcher.animate().translationYBy(mSize).setDuration(ANIME_DURATION);
                }
                if (mIsBokehMode) {
                    mBokehSwitcher.animate().translationYBy(mSize).setDuration(ANIME_DURATION);
                }
                mSceneModeSwitcher.animate().translationYBy(mSize).setDuration(ANIME_DURATION);
                mFilterModeSwitcher.animate().translationYBy(mSize).setDuration(ANIME_DURATION);
                mMenu.animate().translationYBy(mSize).setDuration(ANIME_DURATION);
                mMute.animate().translationYBy(mSize).setDuration(ANIME_DURATION);
                mExitPanorama.animate().translationYBy(mSize).setDuration(ANIME_DURATION);

                mSwitcher.animate().translationYBy(-mSize).setDuration(ANIME_DURATION);
                mShutter.animate().translationYBy(-mSize).setDuration(ANIME_DURATION);
                mVideoShutter.animate().translationYBy(-mSize).setDuration(ANIME_DURATION);
                mPreview.animate().translationYBy(-mSize).setDuration(ANIME_DURATION);
                break;
            case 270:
                mFrontBackSwitcher.animate().translationXBy(mSize).setDuration(ANIME_DURATION);
                if(TsMakeupManager.HAS_TS_MAKEUP) {
                    mTsMakeupSwitcher.animate().translationXBy(mSize).setDuration(ANIME_DURATION);
                } else {
                    mHdrSwitcher.animate().translationXBy(mSize).setDuration(ANIME_DURATION);
                }
                if (mIsBokehMode) {
                    mBokehSwitcher.animate().translationXBy(-mSize).setDuration(ANIME_DURATION);
                }
                mSceneModeSwitcher.animate().translationXBy(mSize).setDuration(ANIME_DURATION);
                mFilterModeSwitcher.animate().translationXBy(mSize).setDuration(ANIME_DURATION);
                mMenu.animate().translationXBy(mSize).setDuration(ANIME_DURATION);
                mMute.animate().translationXBy(mSize).setDuration(ANIME_DURATION);
                mExitPanorama.animate().translationXBy(mSize).setDuration(ANIME_DURATION);

                mSwitcher.animate().translationXBy(-mSize).setDuration(ANIME_DURATION);
                mShutter.animate().translationXBy(-mSize).setDuration(ANIME_DURATION);
                mVideoShutter.animate().translationXBy(-mSize).setDuration(ANIME_DURATION);
                mPreview.animate().translationXBy(-mSize).setDuration(ANIME_DURATION);
                break;
        }
        mRemainingPhotos.setVisibility(View.INVISIBLE);
        mRefocusToast.setVisibility(View.GONE);
    }

    public void showUI() {
        if(!isAnimating)
            enableTouch(false);
        isAnimating = true;
        int rotation = getUnifiedRotation();
        mFrontBackSwitcher.animate().cancel();
        if(TsMakeupManager.HAS_TS_MAKEUP) {
            mTsMakeupSwitcher.animate().cancel();
        } else {
            mHdrSwitcher.animate().cancel();
        }
        if (mIsBokehMode) {
            mBokehSwitcher.animate().cancel();
        }
        mSceneModeSwitcher.animate().cancel();
        mFilterModeSwitcher.animate().cancel();
        mSwitcher.animate().cancel();
        mShutter.animate().cancel();
        mVideoShutter.animate().cancel();
        mMenu.animate().cancel();
        mMute.animate().cancel();
        mExitPanorama.animate().cancel();
        mPreview.animate().cancel();
        if (mViewList != null)
            for (View v : mViewList) {
                v.setVisibility(View.VISIBLE);
            }
        ((ModuleSwitcher) mSwitcher).removePopup();
        AnimationDrawable shutterAnim = (AnimationDrawable) mShutter.getBackground();
        if (shutterAnim != null)
            shutterAnim.stop();

        mMenu.setVisibility(View.VISIBLE);
        mPreview.setVisibility(View.VISIBLE);

        mFrontBackSwitcher.animate().setListener(inlistener);
        switch (rotation) {
            case 0:
                resetLocation(0, -mSize);

                mFrontBackSwitcher.animate().translationYBy(mSize).setDuration(ANIME_DURATION);
                if(TsMakeupManager.HAS_TS_MAKEUP) {
                    mTsMakeupSwitcher.animate().translationYBy(mSize).setDuration(ANIME_DURATION);
                } else {
                    mHdrSwitcher.animate().translationYBy(mSize).setDuration(ANIME_DURATION);
                }
                if (mIsBokehMode) {
                    mBokehSwitcher.animate().translationYBy(mSize).setDuration(ANIME_DURATION);
                }
                mSceneModeSwitcher.animate().translationYBy(mSize).setDuration(ANIME_DURATION);
                mFilterModeSwitcher.animate().translationYBy(mSize).setDuration(ANIME_DURATION);
                mMenu.animate().translationYBy(mSize).setDuration(ANIME_DURATION);
                mMute.animate().translationYBy(mSize).setDuration(ANIME_DURATION);
                mExitPanorama.animate().translationYBy(mSize).setDuration(ANIME_DURATION);

                mSwitcher.animate().translationYBy(-mSize).setDuration(ANIME_DURATION);
                mShutter.animate().translationYBy(-mSize).setDuration(ANIME_DURATION);
                mVideoShutter.animate().translationYBy(-mSize).setDuration(ANIME_DURATION);
                mPreview.animate().translationYBy(-mSize).setDuration(ANIME_DURATION);
                break;
            case 90:
                resetLocation(-mSize, 0);

                mFrontBackSwitcher.animate().translationXBy(mSize).setDuration(ANIME_DURATION);
                if(TsMakeupManager.HAS_TS_MAKEUP) {
                    mTsMakeupSwitcher.animate().translationXBy(mSize).setDuration(ANIME_DURATION);
                } else {
                    mHdrSwitcher.animate().translationXBy(mSize).setDuration(ANIME_DURATION);
                }
                if (mIsBokehMode) {
                    mBokehSwitcher.animate().translationXBy(mSize).setDuration(ANIME_DURATION);
                }
                mSceneModeSwitcher.animate().translationXBy(mSize).setDuration(ANIME_DURATION);
                mFilterModeSwitcher.animate().translationXBy(mSize).setDuration(ANIME_DURATION);
                mMenu.animate().translationXBy(mSize).setDuration(ANIME_DURATION);
                mMute.animate().translationXBy(mSize).setDuration(ANIME_DURATION);
                mExitPanorama.animate().translationXBy(mSize).setDuration(ANIME_DURATION);

                mSwitcher.animate().translationXBy(-mSize).setDuration(ANIME_DURATION);
                mShutter.animate().translationXBy(-mSize).setDuration(ANIME_DURATION);
                mVideoShutter.animate().translationXBy(-mSize).setDuration(ANIME_DURATION);
                mPreview.animate().translationXBy(-mSize).setDuration(ANIME_DURATION);
                break;
            case 180:
                resetLocation(0, mSize);

                mFrontBackSwitcher.animate().translationYBy(-mSize).setDuration(ANIME_DURATION);
                if(TsMakeupManager.HAS_TS_MAKEUP) {
                    mTsMakeupSwitcher.animate().translationYBy(-mSize).setDuration(ANIME_DURATION);
                } else {
                    mHdrSwitcher.animate().translationYBy(-mSize).setDuration(ANIME_DURATION);
                }
                if (mIsBokehMode) {
                    mBokehSwitcher.animate().translationYBy(-mSize).setDuration(ANIME_DURATION);
                }
                mSceneModeSwitcher.animate().translationYBy(-mSize).setDuration(ANIME_DURATION);
                mFilterModeSwitcher.animate().translationYBy(-mSize).setDuration(ANIME_DURATION);
                mMenu.animate().translationYBy(-mSize).setDuration(ANIME_DURATION);
                mMute.animate().translationYBy(-mSize).setDuration(ANIME_DURATION);
                mExitPanorama.animate().translationYBy(-mSize).setDuration(ANIME_DURATION);

                mSwitcher.animate().translationYBy(mSize).setDuration(ANIME_DURATION);
                mShutter.animate().translationYBy(mSize).setDuration(ANIME_DURATION);
                mVideoShutter.animate().translationYBy(mSize).setDuration(ANIME_DURATION);
                mPreview.animate().translationYBy(mSize).setDuration(ANIME_DURATION);
                break;
            case 270:
                resetLocation(mSize, 0);

                mFrontBackSwitcher.animate().translationXBy(-mSize).setDuration(ANIME_DURATION);
                if(TsMakeupManager.HAS_TS_MAKEUP) {
                    mTsMakeupSwitcher.animate().translationXBy(-mSize).setDuration(ANIME_DURATION);
                } else {
                    mHdrSwitcher.animate().translationXBy(-mSize).setDuration(ANIME_DURATION);
                }
                if (mIsBokehMode) {
                    mBokehSwitcher.animate().translationXBy(-mSize).setDuration(ANIME_DURATION);
                }
                mSceneModeSwitcher.animate().translationXBy(-mSize).setDuration(ANIME_DURATION);
                mFilterModeSwitcher.animate().translationXBy(-mSize).setDuration(ANIME_DURATION);
                mMenu.animate().translationXBy(-mSize).setDuration(ANIME_DURATION);
                mMute.animate().translationXBy(-mSize).setDuration(ANIME_DURATION);
                mExitPanorama.animate().translationXBy(-mSize).setDuration(ANIME_DURATION);

                mSwitcher.animate().translationXBy(mSize).setDuration(ANIME_DURATION);
                mShutter.animate().translationXBy(mSize).setDuration(ANIME_DURATION);
                mVideoShutter.animate().translationXBy(mSize).setDuration(ANIME_DURATION);
                mPreview.animate().translationXBy(mSize).setDuration(ANIME_DURATION);
                break;
        }
        if ((mRemainingPhotos.getVisibility() == View.INVISIBLE) &&
                !mHideRemainingPhoto){
            mRemainingPhotos.setVisibility(View.VISIBLE);
        }
        mRefocusToast.setVisibility(View.GONE);
    }

    private void center(View v, Rect other, int rotation) {
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) v.getLayoutParams();
        int tw = lp.leftMargin + v.getMeasuredWidth() + lp.rightMargin;
        int th = lp.topMargin + v.getMeasuredHeight() + lp.bottomMargin;
        int cx = (other.left + other.right) / 2;
        int cy = (other.top + other.bottom) / 2;
        v.layout(cx - tw / 2 + lp.leftMargin,
                cy - th / 2 + lp.topMargin,
                cx + tw / 2 - lp.rightMargin,
                cy + th / 2 - lp.bottomMargin);
    }

    private void toIndex(View v, int w, int h, int rotation, int index, int index2, int index3) {
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) v.getLayoutParams();
        int tw = v.getMeasuredWidth();
        int th = v.getMeasuredHeight();
        int l = 0, r = 0, t = 0, b = 0;

        int wnumber = WIDTH_GRID;
        int hnumber = HEIGHT_GRID;
        int windex = 0;
        int hindex = 0;
        switch (rotation) {
            case 0:
                // portrait, to left of anchor at bottom
                wnumber = WIDTH_GRID;
                hnumber = HEIGHT_GRID;
                windex = index;
                hindex = index2;
                break;
            case 90:
                // phone landscape: below anchor on right
                wnumber = HEIGHT_GRID;
                hnumber = WIDTH_GRID;
                windex = index2;
                hindex = hnumber - index - 1;
                break;
            case 180:
                // phone upside down: right of anchor at top
                wnumber = WIDTH_GRID;
                hnumber = HEIGHT_GRID;
                windex = wnumber - index - 1;
                hindex = hnumber - index2 - 1;
                break;
            case 270:
                // reverse landscape: above anchor on left
                wnumber = HEIGHT_GRID;
                hnumber = WIDTH_GRID;
                windex = wnumber - index2 - 1;
                hindex = index;
                break;
        }
        int boxh = h / hnumber;
        int boxw = w / wnumber;
        int cx = (2 * windex + 1) * boxw / 2;
        int cy = (2 * hindex + 1) * boxh / 2;

        if (index2 == 0 && mTopMargin != 0) {
            switch (rotation) {
                case 90:
                    cx = mTopMargin / 2;
                    break;
                case 180:
                    cy = h - mTopMargin / 2;
                    break;
                case 270:
                    cx = w - mTopMargin / 2;
                    break;
                default:
                    cy = mTopMargin / 2;
                    break;
            }
        }

        l = cx - tw / 2;
        r = cx + tw / 2;
        t = cy - th / 2;
        b = cy + th / 2;

        if (index3 != -1) {
            int idx1 = rotation / 90;
            int idx2 = index3;
            mLocX[idx1][idx2] = l;
            mLocY[idx1][idx2] = t;
        }
        v.layout(l, t, r, b);
    }

    private void toLeft(View v, Rect other, int rotation) {
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) v.getLayoutParams();
        int tw = lp.leftMargin + v.getMeasuredWidth() + lp.rightMargin;
        int th = lp.topMargin + v.getMeasuredHeight() + lp.bottomMargin;
        int cx = (other.left + other.right) / 2;
        int cy = (other.top + other.bottom) / 2;
        int l = 0, r = 0, t = 0, b = 0;
        switch (rotation) {
            case 0:
                // portrait, to left of anchor at bottom
                l = other.left - tw + lp.leftMargin;
                r = other.left - lp.rightMargin;
                t = cy - th / 2 + lp.topMargin;
                b = cy + th / 2 - lp.bottomMargin;
                break;
            case 90:
                // phone landscape: below anchor on right
                l = cx - tw / 2 + lp.leftMargin;
                r = cx + tw / 2 - lp.rightMargin;
                t = other.bottom + lp.topMargin;
                b = other.bottom + th - lp.bottomMargin;
                break;
            case 180:
                // phone upside down: right of anchor at top
                l = other.right + lp.leftMargin;
                r = other.right + tw - lp.rightMargin;
                t = cy - th / 2 + lp.topMargin;
                b = cy + th / 2 - lp.bottomMargin;
                break;
            case 270:
                // reverse landscape: above anchor on left
                l = cx - tw / 2 + lp.leftMargin;
                r = cx + tw / 2 - lp.rightMargin;
                t = other.top - th + lp.topMargin;
                b = other.top - lp.bottomMargin;
                break;
        }
        v.layout(l, t, r, b);
    }

    private void toRight(View v, Rect other, int rotation) {
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) v.getLayoutParams();
        int tw = lp.leftMargin + v.getMeasuredWidth() + lp.rightMargin;
        int th = lp.topMargin + v.getMeasuredHeight() + lp.bottomMargin;
        int cx = (other.left + other.right) / 2;
        int cy = (other.top + other.bottom) / 2;
        int l = 0, r = 0, t = 0, b = 0;
        switch (rotation) {
            case 0:
                l = other.right + lp.leftMargin;
                r = other.right + tw - lp.rightMargin;
                t = cy - th / 2 + lp.topMargin;
                b = cy + th / 2 - lp.bottomMargin;
                break;
            case 90:
                l = cx - tw / 2 + lp.leftMargin;
                r = cx + tw / 2 - lp.rightMargin;
                t = other.top - th + lp.topMargin;
                b = other.top - lp.bottomMargin;
                break;
            case 180:
                l = other.left - tw + lp.leftMargin;
                r = other.left - lp.rightMargin;
                t = cy - th / 2 + lp.topMargin;
                b = cy + th / 2 - lp.bottomMargin;
                break;
            case 270:
                l = cx - tw / 2 + lp.leftMargin;
                r = cx + tw / 2 - lp.rightMargin;
                t = other.bottom + lp.topMargin;
                b = other.bottom + th - lp.bottomMargin;
                break;
        }
        v.layout(l, t, r, b);
    }

    private void adjustBackground() {
        int rotation = getUnifiedRotation();
        // remove current drawable and reset rotation
        mBackgroundView.setBackgroundDrawable(null);
        mBackgroundView.setRotationX(0);
        mBackgroundView.setRotationY(0);
        // if the switcher background is top aligned we need to flip the
        // background
        // drawable vertically; if left aligned, flip horizontally
        switch (rotation) {
            case 180:
                mBackgroundView.setRotationX(180);
                break;
            case 270:
                mBackgroundView.setRotationY(180);
                break;
            default:
                break;
        }
        mBackgroundView.setBackgroundResource(R.drawable.switcher_bg);
    }

    private void layoutRemaingPhotos() {
        int rl = mPreview.getLeft();
        int rt = mPreview.getTop();
        int rr = mPreview.getRight();
        int rb = mPreview.getBottom();
        int w = mRemainingPhotos.getMeasuredWidth();
        int h = mRemainingPhotos.getMeasuredHeight();
        int m = getResources().getDimensionPixelSize(R.dimen.remaining_photos_margin);

        int hc = (rl + rr) / 2;
        int vc = (rt + rb) / 2 - m;
        if (mOrientation == 90 || mOrientation == 270) {
            vc -= w / 2;
        }
        if(hc < w/2) {
            mRemainingPhotos.layout(0, vc - h / 2, w, vc + h / 2);
        } else {
            mRemainingPhotos.layout(hc - w / 2, vc - h / 2, hc + w / 2, vc + h / 2);
        }
        mRemainingPhotos.setRotation(-mOrientation);
    }

    public void updateRemainingPhotos(int remaining) {
        long remainingStorage = Storage.getAvailableSpace() - Storage.LOW_STORAGE_THRESHOLD_BYTES;
        if ((remaining < 0 && remainingStorage <= 0) || mHideRemainingPhoto) {
            mRemainingPhotos.setVisibility(View.GONE);
        } else {
            for (int i = mRemainingPhotos.getChildCount() - 1; i >= 0; --i) {
                mRemainingPhotos.getChildAt(i).setVisibility(View.VISIBLE);
            }
            if (remaining < LOW_REMAINING_PHOTOS) {
                mRemainingPhotosText.setText("<" + LOW_REMAINING_PHOTOS + " ");
            } else if (remaining >= HIGH_REMAINING_PHOTOS) {
                mRemainingPhotosText.setText(">" + HIGH_REMAINING_PHOTOS);
            } else {
                mRemainingPhotosText.setText(remaining + " ");
            }
        }
        mCurrentRemaining = remaining;
    }

    public void setMargins(int top, int bottom) {
        mTopMargin = top;
        mBottomMargin = bottom;
    }

    public void setPreviewRatio(float ratio, boolean panorama) {
        if (panorama) {
            mPaint.setColor(Color.TRANSPARENT);
        } else {
            int r = CameraUtil.determineRatio(ratio);
            mPreviewRatio = r;
            if (mPreviewRatio == CameraUtil.RATIO_4_3 && mTopMargin != 0) {
                mPaint.setColor(getResources().getColor(R.color.camera_control_bg_opaque));
            } else {
                mPaint.setColor(getResources().getColor(R.color.camera_control_bg_transparent));
            }
        }
        requestLayout();
    }

    public void showRefocusToast(boolean show) {
        mRefocusToast.setVisibility(show ? View.VISIBLE : View.GONE);
        if ((mCurrentRemaining > 0 ) && !mHideRemainingPhoto) {
            mRemainingPhotos.setVisibility(show ? View.GONE : View.VISIBLE);
        }
    }

    public void setOrientation(int orientation, boolean animation) {
        mOrientation = orientation;
        View[] views = {
            mSceneModeSwitcher, mFilterModeSwitcher, mFrontBackSwitcher,
            TsMakeupManager.HAS_TS_MAKEUP ? mTsMakeupSwitcher : mHdrSwitcher,mBokehSwitcher,
            mMenu, mPreview, mSwitcher, mMute, mReviewRetakeButton,
            mReviewCancelButton, mReviewDoneButton, mExitPanorama
        };
        for (View v : views) {
            if (v != null) {
                ((RotateImageView) v).setOrientation(orientation,
                                                     animation);
            }
        }
        layoutRemaingPhotos();
    }

    public void hideCameraSettings() {
        mFrontBackSwitcher.setVisibility(View.INVISIBLE);
        if(TsMakeupManager.HAS_TS_MAKEUP) {
            mTsMakeupSwitcher.setVisibility(View.INVISIBLE);
        } else {
            mHdrSwitcher.setVisibility(View.INVISIBLE);
        }
        if (mIsBokehMode) {
            mBokehSwitcher.setVisibility(View.INVISIBLE);
        }
        mSceneModeSwitcher.setVisibility(View.INVISIBLE);
        mFilterModeSwitcher.setVisibility(View.INVISIBLE);
        mMenu.setVisibility(View.INVISIBLE);
    }

    public void showCameraSettings() {
        mFrontBackSwitcher.setVisibility(View.VISIBLE);
        if(TsMakeupManager.HAS_TS_MAKEUP) {
            mTsMakeupSwitcher.setVisibility(View.VISIBLE);
        } else {
            mHdrSwitcher.setVisibility(View.VISIBLE);
        }
        if (mIsBokehMode) {
            mBokehSwitcher.setVisibility(View.VISIBLE);
        }
        mSceneModeSwitcher.setVisibility(View.VISIBLE);
        mFilterModeSwitcher.setVisibility(View.VISIBLE);
        mMenu.setVisibility(View.VISIBLE);
    }

    public void hideRemainingPhotoCnt() {
        mHideRemainingPhoto = true;
        mRemainingPhotos.setVisibility(View.GONE);
        mRemainingPhotosText.setVisibility(View.GONE);
    }

    public void showRemainingPhotoCnt() {
        mHideRemainingPhoto = false;
        mRemainingPhotos.setVisibility(View.VISIBLE);
        mRemainingPhotosText.setVisibility(View.VISIBLE);
    }

    private class ArrowTextView extends TextView {
        private static final int TEXT_SIZE = 14;
        private static final int PADDING_SIZE = 18;
        private static final int BACKGROUND = 0x80000000;

        private Paint mPaint;
        private Path mPath;

        public ArrowTextView(Context context) {
            super(context);

            setText(context.getString(R.string.refocus_toast));
            setBackgroundColor(BACKGROUND);
            setVisibility(View.GONE);
            setLayoutParams(new ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT));
            setTextSize(TEXT_SIZE);
            setPadding(PADDING_SIZE, PADDING_SIZE, PADDING_SIZE, PADDING_SIZE);

            mPaint = new Paint();
            mPaint.setStyle(Paint.Style.FILL);
            mPaint.setColor(BACKGROUND);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            if (mPath != null) {
                canvas.drawPath(mPath, mPaint);
            }
        }

        public void setArrow(float x1, float y1, float x2, float y2, float x3, float y3) {
            mPath = new Path();
            mPath.reset();
            mPath.moveTo(x1, y1);
            mPath.lineTo(x2, y2);
            mPath.lineTo(x3, y3);
            mPath.lineTo(x1, y1);
        }
    }
}
