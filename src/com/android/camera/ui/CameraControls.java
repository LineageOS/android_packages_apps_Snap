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
import android.graphics.Path;
import android.graphics.Rect;
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
import com.android.camera.util.CameraUtil;

public class CameraControls extends RotatableLayout {

    private static final String TAG = "CAM_Controls";

    private View mBackgroundView;
    private View mShutter;
    private View mSwitcher;
    private View mMenu;
    private View mFrontBackSwitcher;
    private View mHdrSwitcher;
    private View mIndicators;
    private View mPreview;
    private View mSceneModeSwitcher;
    private View mFilterModeSwitcher;
    private ArrowTextView mRefocusToast;

    private int mSize;
    private static final int WIDTH_GRID = 5;
    private static final int HEIGHT_GRID = 7;
    private static boolean isAnimating = false;
    private ArrayList<View> mViewList;
    private View[] mAllViews, mTopViews, mBottomViews;
    private static final int ANIME_DURATION = 300;
    private boolean mFilterModeEnabled;

    private LinearLayout mRemainingPhotos;
    private TextView mRemainingPhotosText;
    private int mOrientation;
    private final Rect mInsets = new Rect();

    private int mTopMargin = 0;
    private int mBottomMargin = 0;

    private Paint mPaint;

    AnimatorListener outlistener = new AnimatorListener() {
        @Override
        public void onAnimationStart(Animator animation) {
        }

        @Override
        public void onAnimationRepeat(Animator animation) {

        }

        @Override
        public void onAnimationEnd(Animator animation) {
            for (View v : mAllViews) {
                v.setVisibility(View.INVISIBLE);
            }
            isAnimating = false;
            enableTouch(true);
        }

        @Override
        public void onAnimationCancel(Animator animation) {
            for (View v : mAllViews) {
                v.setVisibility(View.INVISIBLE);
            }
            isAnimating = false;
            enableTouch(true);
        }
    };

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
            enableTouch(true);
        }

        @Override
        public void onAnimationCancel(Animator animation) {
            isAnimating = false;
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
            mShutter.setPressed(false);
            mSwitcher.setPressed(false);
            mMenu.setPressed(false);
            mFrontBackSwitcher.setPressed(false);
            mHdrSwitcher.setPressed(false);
            mSceneModeSwitcher.setPressed(false);
            mFilterModeSwitcher.setPressed(false);
        } else {
            mFilterModeEnabled = mFilterModeSwitcher.isEnabled();
        }
        ((ShutterButton) mShutter).enableTouch(enable);
        ((ModuleSwitcher) mSwitcher).enableTouch(enable);
        mMenu.setEnabled(enable);
        mFrontBackSwitcher.setEnabled(enable);
        mHdrSwitcher.setEnabled(enable);
        mSceneModeSwitcher.setEnabled(enable);
        mPreview.setEnabled(enable);
        mFilterModeSwitcher.setEnabled(enable && mFilterModeEnabled);
    }

    private void markVisibility() {
        mViewList = new ArrayList<View>();
        for (View v : mAllViews) {
            if (v.getVisibility() == View.VISIBLE) {
                mViewList.add(v);
            }
        }
    }

    @Override
    public void onFinishInflate() {
        super.onFinishInflate();
        mBackgroundView = findViewById(R.id.blocker);
        mSwitcher = findViewById(R.id.camera_switcher);
        mShutter = findViewById(R.id.shutter_button);
        mFrontBackSwitcher = findViewById(R.id.front_back_switcher);
        mHdrSwitcher = findViewById(R.id.hdr_switcher);
        mMenu = findViewById(R.id.menu);
        mIndicators = findViewById(R.id.on_screen_indicators);
        mPreview = findViewById(R.id.preview_thumb);
        mSceneModeSwitcher = findViewById(R.id.scene_mode_switcher);
        mFilterModeSwitcher = findViewById(R.id.filter_mode_switcher);
        mRemainingPhotos = (LinearLayout) findViewById(R.id.remaining_photos);
        mRemainingPhotosText = (TextView) findViewById(R.id.remaining_photos_text);

        mTopViews = new View[] {
            mSceneModeSwitcher, mFilterModeSwitcher, mHdrSwitcher,
            mFrontBackSwitcher, mMenu
        };
        mBottomViews = new View[] {
            mIndicators, mPreview, mShutter, mSwitcher
        };
        mAllViews = new View[mTopViews.length + mBottomViews.length];
        for (int i = 0; i < mTopViews.length; i++) {
            mAllViews[i] = mTopViews[i];
        }
        for (int i = 0; i < mBottomViews.length; i++) {
            mAllViews[i + mTopViews.length] = mBottomViews[i];
        }
    }

    @Override
    protected boolean fitSystemWindows(Rect insets) {
        mInsets.set(insets);
        return false;
    }

    @Override
    public void onLayout(boolean changed, int l, int t, int r, int b) {
        int orientation = getResources().getConfiguration().orientation;
        int size = getResources().getDimensionPixelSize(R.dimen.camera_controls_size);
        int rotation = getUnifiedRotation();
        adjustBackground();
        // As l,t,r,b are positions relative to parents, we need to convert them
        // to child's coordinates
        r = r - l - mInsets.right;
        b = b - t - mInsets.bottom;
        l = 0;
        t = 0;
        for (int i = 0; i < getChildCount(); i++) {
            View v = getChildAt(i);
            if (v == mBackgroundView) {
                v.layout(l, t, r + mInsets.right, b + mInsets.bottom);
            } else {
                v.layout(l, t, r, b);
            }
        }

        Rect shutter = new Rect();
        center(mShutter, l, t, r, b, orientation, rotation, shutter);
        mSize = (int) (Math.max(shutter.right - shutter.left, shutter.bottom - shutter.top) * 1.2f);
        center(mBackgroundView, l, t, r + mInsets.right, b + mInsets.bottom,
                orientation, rotation, new Rect());
        mBackgroundView.setVisibility(View.GONE);

        int w = r - l;
        int h = b - t;
        asRow(true, w, h, rotation, mSceneModeSwitcher, mFilterModeSwitcher,
                mFrontBackSwitcher, mHdrSwitcher, mMenu);

        Rect expandedShutter = new Rect(shutter);
        switch (rotation) {
            case 90:
                expandedShutter.top = determineWidth(mSwitcher);
                expandedShutter.bottom = h - determineWidth(mPreview);
                break;
            case 180:
                expandedShutter.left = determineWidth(mSwitcher);
                expandedShutter.right = w - determineWidth(mPreview);
                break;
            case 270:
                expandedShutter.top = determineWidth(mPreview);
                expandedShutter.bottom = h - determineWidth(mSwitcher);
                break;
            default:
                expandedShutter.left = determineWidth(mPreview);
                expandedShutter.right = w - determineWidth(mSwitcher);
                break;
        }

        toRight(mSwitcher, expandedShutter, rotation);
        toLeft(mIndicators, expandedShutter, rotation);
        toLeft(mPreview, expandedShutter, rotation);

        layoutToast(mRefocusToast, w, h, rotation);

        View retake = findViewById(R.id.btn_retake);
        if (retake != null) {
            center(retake, shutter, rotation);
            View cancel = findViewById(R.id.btn_cancel);
            toLeft(cancel, shutter, rotation);
            View done = findViewById(R.id.btn_done);
            toRight(done, shutter, rotation);
        }

        layoutRemaingPhotos();
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

    private int determineWidth(View v) {
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) v.getLayoutParams();
        return v.getMeasuredWidth() + lp.leftMargin + lp.rightMargin;
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
            Rect result) {
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
    }

    public void hideUI() {
        if(!isAnimating)
            enableTouch(false);
        isAnimating = true;
        int rotation = getUnifiedRotation();
        for (View v : mAllViews) {
            v.animate().cancel();
        }
        mFrontBackSwitcher.animate().setListener(outlistener);
        ((ModuleSwitcher) mSwitcher).removePopup();
        markVisibility();
        int topTranslation = rotation == 0 || rotation == 90 ? -mSize : mSize;
        boolean isYTranslation = rotation == 0 || rotation == 180;
        animateViews(topTranslation, isYTranslation);
        //mRemainingPhotos.getVisibility(View.INVISIBLE);
        mRefocusToast.setVisibility(View.GONE);
    }

    private void animateViews(int topTranslation, boolean isYTranslation) {
        animateViews(mTopViews, topTranslation, isYTranslation);
        animateViews(mBottomViews, -topTranslation, isYTranslation);
    }

    private void animateViews(View[] views, int translation, boolean isYTranslation) {
        for (View v : views) {
            ViewPropertyAnimator vpa = v.animate();
            if (isYTranslation) {
                vpa.translationYBy(translation);
            } else {
                vpa.translationXBy(translation);
            }
            vpa.setDuration(ANIME_DURATION);
        }
    }

    public void showUI() {
        if(!isAnimating)
            enableTouch(false);
        isAnimating = true;
        int rotation = getUnifiedRotation();
        for (View v : mAllViews) {
            v.animate().cancel();
        }
        if (mViewList != null)
            for (View v : mViewList) {
                v.setVisibility(View.VISIBLE);
            }
        ((ModuleSwitcher) mSwitcher).removePopup();
        AnimationDrawable shutterAnim = (AnimationDrawable) mShutter.getBackground();
        if (shutterAnim != null)
            shutterAnim.stop();

        mMenu.setVisibility(View.VISIBLE);
        mIndicators.setVisibility(View.VISIBLE);
        mPreview.setVisibility(View.VISIBLE);

        mFrontBackSwitcher.animate().setListener(inlistener);
        int topTranslation = rotation == 0 || rotation == 90 ? mSize : -mSize;
        boolean isYTranslation = rotation == 0 || rotation == 180;
        animateViews(topTranslation, isYTranslation);
        /*if (mRemainingPhotos.getVisibility() == View.INVISIBLE) {
            mRemainingPhotos.setVisibility(View.VISIBLE);
        }*/
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

    private void asRow(boolean top, int w, int h, int rotation, View...views) {
        int largestHeight = 0;
        for (View v : views) {
            largestHeight = Math.max(largestHeight, v.getMeasuredHeight());
        }

        boolean landscape = rotation == 90 || rotation == 270;
        int usedWidth = landscape ? h : w;
        int usedHeight = landscape ? w : h;
        int cellWidth = usedWidth / views.length;
        int leftOffset = (usedWidth - cellWidth * views.length) / 2;
        final int cellHeight;

        if (mTopMargin != 0 && top) {
            cellHeight = Math.max(largestHeight, mTopMargin);
        } else if (mBottomMargin != 0 && !top) {
            cellHeight = Math.max(largestHeight, mBottomMargin);
        } else {
            cellHeight = Math.max(largestHeight, mSize);
        }

        for (int i = 0; i < views.length; i++) {
            View v = views[i];
            final int cx, cy;

            switch (rotation) {
                case 90:
                    cx = (top ? cellHeight / 2 : usedHeight - cellHeight / 2);
                    cy = usedWidth - leftOffset - cellWidth * i - (cellWidth / 2);
                    break;
                case 270:
                    cx = top ? usedHeight - cellHeight / 2 : cellHeight / 2;
                    cy = leftOffset + cellWidth * i + (cellWidth / 2);
                    break;
                case 180:
                    cx = usedWidth - leftOffset - cellWidth * i - (cellWidth / 2);
                    cy = top ? usedHeight - cellHeight / 2 : cellHeight / 2;
                    break;
                default:
                    cx = leftOffset + cellWidth * i + (cellWidth / 2);
                    cy = (top ? cellHeight / 2 : usedHeight - cellHeight / 2);
                    break;
            }

            int tw = v.getMeasuredWidth();
            int th = v.getMeasuredHeight();
            v.layout(cx - tw / 2, cy - th / 2, cx + tw / 2, cy + tw / 2);
        }
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
        mRemainingPhotos.layout(hc - w / 2, vc - h / 2, hc + w / 2, vc + h / 2);
        mRemainingPhotos.setRotation(-mOrientation);
    }

    public void updateRemainingPhotos(int remaining) {
        if (remaining < 0) {
            mRemainingPhotos.setVisibility(View.GONE);
        } else {
            mRemainingPhotos.setVisibility(View.VISIBLE);
            mRemainingPhotosText.setText(remaining + " ");
        }
    }

    public void setMargins(int top, int bottom) {
        mTopMargin = top;
        mBottomMargin = bottom;
    }

    public void setPreviewRatio(float ratio, boolean panorama) {
        if (panorama) {
            mPaint.setColor(Color.TRANSPARENT);
        } else {
            int previewRatio = CameraUtil.determineRatio(ratio);
            if (previewRatio == CameraUtil.RATIO_4_3 && mTopMargin != 0) {
                mPaint.setColor(getResources().getColor(R.color.camera_control_bg_opaque));
            } else {
                mPaint.setColor(getResources().getColor(R.color.camera_control_bg_transparent));
            }
        }
        invalidate();
    }

    public void showRefocusToast(boolean show) {
        mRefocusToast.setVisibility(show ? View.VISIBLE : View.GONE);
        mRemainingPhotos.setVisibility(show ? View.GONE : View.VISIBLE);
    }

    public void setOrientation(int orientation, boolean animation) {
        mOrientation = orientation;
        for (View v : mAllViews) {
            if (v instanceof RotateImageView) {
                ((RotateImageView) v).setOrientation(orientation, animation);
            }
        }
        layoutRemaingPhotos();
    }

    public void hideCameraSettings() {
        for (View v : mTopViews) {
            v.setVisibility(View.INVISIBLE);
        }
    }

    public void showCameraSettings() {
        for (View v : mTopViews) {
            v.setVisibility(View.VISIBLE);
        }
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
