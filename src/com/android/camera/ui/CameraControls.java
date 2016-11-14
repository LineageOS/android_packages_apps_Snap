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
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.app.Activity;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.RippleDrawable;
import android.util.AttributeSet;
import android.util.Log;
import android.util.Pair;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.android.camera.CameraActivity;
import com.android.camera.CameraManager;
import com.android.camera.ShutterButton;
import com.android.camera.Storage;
import com.android.camera.TsMakeupManager;
import com.android.camera.util.CameraUtil;

import org.codeaurora.snapcam.R;

import java.util.ArrayList;
import java.util.List;

public class CameraControls extends RotatableLayout {

    private static final String TAG = "CAM_Controls";

    private LinearLayout mTopBar;
    private LinearLayout mBottomBar;

    private ShutterButton mShutter;
    private View mVideoShutter;
    private ModuleSwitcher mSwitcher;
    private View mTsMakeupSwitcher;
    private View mThumbnail;
    private View mAutoHdrNotice;
    private HistogramView mHistogramView;
    private ArrowTextView mRefocusToast;
    private View mFrontBackSwitcher;
    private View mMenu;

    private View mReviewDoneButton;
    private View mReviewCancelButton;
    private View mReviewRetakeButton;

    private final List<View> mViews = new ArrayList<>();

    private static final int WIDTH_GRID = 5;
    private static final int HEIGHT_GRID = 7;
    private boolean mHidden = false;
    private AnimatorSet mAnimator = null;
    private boolean mFullyHidden = false;

    private static final int ANIME_DURATION = 300;

    private boolean mHideRemainingPhoto = false;
    private LinearLayout mRemainingPhotos;
    private TextView mRemainingPhotosText;
    private int mCurrentRemaining = -1;
    private int mOrientation;

    private int mPreviewRatio;
    private int mTopMargin = 0;
    private int mBottomMargin = 0;

    private static final int LOW_REMAINING_PHOTOS = 20;
    private static final int HIGH_REMAINING_PHOTOS = 1000000;

    private int mModuleIndex = -1;

    AnimatorListener outlistener = new AnimatorListenerAdapter() {
        @Override
        public void onAnimationStart(Animator animation) {
            enableTouch(false);
        }
        @Override
        public void onAnimationEnd(Animator animation) {
            setChildrenVisibility(mBottomBar, false);
            if (mFullyHidden) {
                setChildrenVisibility(mTopBar, false);
                setVisibility(View.INVISIBLE);
            }
        }
    };

    AnimatorListener inlistener = new AnimatorListenerAdapter() {
        @Override
        public void onAnimationStart(Animator animation) {
            setChildrenVisibility(mBottomBar, true);
            if (mFullyHidden) {
                setChildrenVisibility(mTopBar, true);
                setVisibility(View.VISIBLE);
                mFullyHidden = false;
            }
        }
        @Override
        public void onAnimationEnd(Animator animation) {
            enableTouch(true);
        }
    };

    private void setChildrenVisibility(ViewGroup parent, boolean visible) {
        for (int i = 0; i < parent.getChildCount(); i++) {
            View v = parent.getChildAt(i);
            if (v.getVisibility() != View.GONE) {
                v.setVisibility(visible ? View.VISIBLE : View.INVISIBLE);
            }
        }
    }

    public CameraControls(Context context, AttributeSet attrs) {
        super(context, attrs);
        mRefocusToast = new ArrowTextView(context);
        addView(mRefocusToast);
        setClipChildren(false);

        setMeasureAllChildren(true);

        Pair<Integer, Integer> margins = CameraUtil.calculateMargins((Activity)context);
        mTopMargin = margins.first;
        mBottomMargin = margins.second;
    }

    public CameraControls(Context context) {
        this(context, null);
    }

    public boolean isAnimating() {
        return mAnimator != null && mAnimator.isRunning();
    }

    public void enableTouch(boolean enable) {
        Log.d(TAG, "ENABLE TOUCH " + enable + " mViews.size=" + mViews.size());

        synchronized (mViews) {
            for (View v : mViews) {
                if (v.getVisibility() != View.GONE) {
                    if (enable) {
                        v.setPressed(false);
                    }
                    v.setEnabled(enable);
                }
            }
        }

        ((ShutterButton) mShutter).enableTouch(enable);
        mVideoShutter.setClickable(enable);
        mTopBar.setEnabled(enable);
        mBottomBar.setEnabled(enable);
    }

    public void removeFromViewList(View view) {
        synchronized (mViews) {
            if (view == null || !mViews.contains(view)) {
                return;
            }
            view.setVisibility(View.GONE);
            removeView(view);
            mViews.remove(view);
            requestLayout();
        }
    }

    @Override
    public void onFinishInflate() {
        super.onFinishInflate();
        mTopBar = (LinearLayout) findViewById(R.id.top_bar);
        mBottomBar = (LinearLayout) findViewById(R.id.bottom_bar);
        mSwitcher = (ModuleSwitcher) findViewById(R.id.camera_switcher);
        mShutter = (ShutterButton) findViewById(R.id.shutter_button);
        mVideoShutter = findViewById(R.id.video_button);
        mTsMakeupSwitcher = findViewById(R.id.ts_makeup_switcher);
        mThumbnail = findViewById(R.id.preview_thumb);
        mRemainingPhotos = (LinearLayout) findViewById(R.id.remaining_photos);
        mRemainingPhotosText = (TextView) findViewById(R.id.remaining_photos_text);
        mAutoHdrNotice = (TextView) findViewById(R.id.auto_hdr_notice);
        mHistogramView = (HistogramView) findViewById(R.id.histogram);
        mFrontBackSwitcher = findViewById(R.id.front_back_switcher);
        mMenu = findViewById(R.id.menu);

        if (!TsMakeupManager.HAS_TS_MAKEUP) {
            mTopBar.removeView(mTsMakeupSwitcher);
        }

        synchronized (mViews) {
            for (int i = 0; i < mTopBar.getChildCount(); i++) {
                mViews.add(mTopBar.getChildAt(i));
            }

            for (int i = 0; i < mBottomBar.getChildCount(); i++) {
                mViews.add(mBottomBar.getChildAt(i));
            }

            mViews.add(mAutoHdrNotice);
            mViews.add(mHistogramView);
        }

        mShutter.addOnShutterButtonListener(mShutterListener);

        mSwitcher.setSwitchListener((CameraActivity)getContext());
        mSwitcher.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                mSwitcher.showPopup();
                mSwitcher.setOrientation(mOrientation, false);
            }
        });
        if (mModuleIndex >= 0) {
            mSwitcher.setCurrentIndex(mModuleIndex);
        }
    }

    public void setModuleIndex(int index) {
        mModuleIndex = index;
        if (mSwitcher != null) {
            mSwitcher.setCurrentIndex(index);
        }
    }

    public void hideSwitcher() {
        if (mSwitcher != null) {
            mSwitcher.closePopup();
            mSwitcher.setEnabled(false);
        }
    }

    public void showSwitcher() {
        if (mSwitcher != null) {
            mSwitcher.setEnabled(true);
        }
    }

    public void collapse() {
        if (mSwitcher != null) {
            mSwitcher.closePopup();
        }
    }

    private ShutterButton.OnShutterButtonListener mShutterListener = new ShutterButton.OnShutterButtonListener() {
        @Override
        public void onShutterButtonFocus(boolean pressed) {
            if (pressed) {
                showRipples(mBottomBar, true);
            } else {
                showRipples(mBottomBar, false);
            }
        }

        @Override
        public void onShutterButtonClick() {
            showRipples(mBottomBar, true);
            showRipples(mBottomBar, false);
        }

        @Override
        public void onShutterButtonLongClick() {

        }
    };

    public void disableMuteButton() {
        removeFromViewList(findViewById(R.id.mute_button));
    }

    public void disableSceneModes() {
        removeFromViewList(findViewById(R.id.scene_mode_switcher));
        removeFromViewList(findViewById(R.id.hdr_switcher));
    }

    @Override
    public void onLayout(boolean changed, int l, int t, int r, int b) {
        Log.d(TAG, String.format("onLayout changed=%b l=%d t=%d r=%d b=%d", changed, l, t, r, b));

        super.onLayout(changed, l, t, r, b);

        ViewGroup.LayoutParams lpTop = mTopBar.getLayoutParams();
        lpTop.height = mTopMargin;
        mTopBar.setLayoutParams(lpTop);
        mTopBar.layout(l, t, r, mTopMargin);

        ViewGroup.LayoutParams lpBottom = mBottomBar.getLayoutParams();
        lpBottom.height = mBottomMargin;
        mBottomBar.setLayoutParams(lpBottom);
        mBottomBar.layout(l, b - mBottomMargin, r, b);

        setLocation(r - l, b - t);

        mAutoHdrNotice.layout(l, t + mTopMargin,
                r, t + mTopMargin + mAutoHdrNotice.getMeasuredHeight());

        mHistogramView.layout(l, b - mBottomMargin - mHistogramView.getMeasuredHeight(),
                r, b - mBottomMargin);

        View retake = findViewById(R.id.btn_retake);
        if (retake != null) {
            mReviewRetakeButton = retake;
            mReviewCancelButton = findViewById(R.id.btn_cancel);
            mReviewDoneButton = findViewById(R.id.btn_done);

            /*
            center(mReviewRetakeButton, shutter, rotation);
            toLeft(mReviewCancelButton, shutter, rotation);
            toRight(mReviewDoneButton, shutter, rotation);
            */
        } else {
            mReviewRetakeButton = null;
            mReviewCancelButton = null;
            mReviewDoneButton = null;
        }
        layoutRemaingPhotos();
    }

    private void showRipples(final View v, final boolean enable) {
        v.post(new Runnable() {
            @Override
            public void run() {
                Drawable background = v.getBackground();
                if (background instanceof RippleDrawable) {
                    RippleDrawable ripple = (RippleDrawable) background;
                    if (enable) {
                        ripple.setState(new int[]{android.R.attr.state_pressed, android.R.attr.state_enabled});
                    } else {
                        ripple.setState(new int[]{});
                    }
                }
            }
        });
    }

    @Override
    public void onAttachedToWindow() {
        super.onAttachedToWindow();
        mTopBar.setTranslationX(0);
        mTopBar.setTranslationY(0);
        mBottomBar.setTranslationX(0);
        mBottomBar.setTranslationY(0);
        mHidden = false;
        mFullyHidden = false;
        setChildrenVisibility(mTopBar, true);
        setChildrenVisibility(mBottomBar, true);
    }

    private void setLocation(int w, int h) {
        int rotation = getUnifiedRotation();
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
                mRefocusToast.setArrow(tw - th / 2, 0, tw, 0, tw, -th / 2);
                break;
            case 270:
                c = (int) (h / WIDTH_GRID * 0.5);
                t = c - th / 2;
                b = c + th / 2;
                l = (int) (w / HEIGHT_GRID * 1.25);
                r = l + tw;
                mRefocusToast.setArrow(0, 0, 0, th / 2, -th / 2, 0);
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

    public void setTitleBarVisibility(int status) {
        mTopBar.setVisibility(status);
    }

    public void setAutoHdrEnabled(boolean enabled) {
        mAutoHdrNotice.setVisibility(enabled ? View.VISIBLE : View.GONE);
    }

    private ObjectAnimator getViewAnimation(View v, float distance, boolean vertical) {
        final ObjectAnimator anim = ObjectAnimator.ofFloat(v,
                (vertical ? "translationY" : "translationX"), distance);
        anim.setDuration(ANIME_DURATION);
        return anim;
    }

    private void animateViews(boolean enabled, boolean full, AnimatorListener listener) {
        int rotation = getUnifiedRotation();
        int topSize = enabled ? 0 : -mTopMargin;
        int bottomSize = enabled ? 0 : mBottomMargin;
        boolean vertical = true;
        switch (rotation) {
            case 0:
                break;
            case 90:
                vertical = false;
                break;
            case 180:
                topSize = -topSize;
                bottomSize = -bottomSize;
                break;
            case 270:
                topSize = -topSize;
                bottomSize = -bottomSize;
                vertical = false;
                break;
        }

        mAnimator = new AnimatorSet();
        mAnimator.addListener(listener);
        mAnimator.setDuration(ANIME_DURATION);
        mAnimator.setInterpolator(enabled ?
                new DecelerateInterpolator() :
                new AccelerateInterpolator());

        if (full) {
            mAnimator.playTogether(getViewAnimation(mTopBar, topSize, vertical),
                                   getViewAnimation(mBottomBar, bottomSize, vertical));
        } else {
            final ArrayList<ObjectAnimator> anims = new ArrayList<>();
            for (int i = 0; i < mBottomBar.getChildCount(); i++) {
                View v = mBottomBar.getChildAt(i);
                if (v.getVisibility() != View.GONE) {
                    anims.add(getViewAnimation(v, bottomSize, vertical));
                }
            }
            if (anims.size() > 0) {
                mAnimator.playTogether(anims.toArray(new ObjectAnimator[anims.size()]));
            }
        }
        mAnimator.start();
    }

    public void setMenuAndSwitcherEnabled(boolean enable) {
        mMenu.setEnabled(enable);
        mFrontBackSwitcher.setEnabled(enable);
    }

    public void hideUI(boolean toBlack) {
        if (mHidden) {
            return;
        }

        mHidden = true;
        mFullyHidden = toBlack;

        if (isAnimating()) {
            mAnimator.cancel();
        }

        collapse();

        animateViews(false, toBlack, outlistener);

        mRemainingPhotos.setVisibility(View.INVISIBLE);
        mRefocusToast.setVisibility(View.GONE);
    }

    public void showUI() {
        if (!mHidden) {
            return;
        }

        mHidden = false;

        if (isAnimating()) {
            mAnimator.cancel();
        }

        animateViews(true, mFullyHidden, inlistener);

        if ((mRemainingPhotos.getVisibility() == View.INVISIBLE) &&
                !mHideRemainingPhoto) {
            mRemainingPhotos.setVisibility(View.VISIBLE);
        }
        mRefocusToast.setVisibility(View.GONE);
    }

    private void layoutRemaingPhotos() {
        int rl = mThumbnail.getLeft();
        int rt = mThumbnail.getTop();
        int rr = mThumbnail.getRight();
        int rb = mThumbnail.getBottom();
        int w = mRemainingPhotos.getMeasuredWidth();
        int h = mRemainingPhotos.getMeasuredHeight();
        int m = getResources().getDimensionPixelSize(R.dimen.remaining_photos_margin);

        int hc = (rl + rr) / 2;
        int vc = (rt + rb) / 2 - m;
        if (mOrientation == 90 || mOrientation == 270) {
            vc -= w / 2;
        }
        if (hc < w / 2) {
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

    public boolean arePreviewControlsVisible() {
        return !mHidden;
    }

    public void setPreviewRect(RectF rectL) {
        int r = CameraUtil.determineRatio(Math.round(rectL.width()), Math.round(rectL.height()));
        mPreviewRatio = r;
        if (mPreviewRatio == CameraUtil.RATIO_4_3 && mTopMargin != 0) {
            mBottomBar.setBackgroundResource(R.drawable.camera_controls_bg_opaque);
        } else {
            mBottomBar.setBackgroundResource(R.drawable.camera_controls_bg_translucent);
        }
    }

    public void showRefocusToast(boolean show) {
        mRefocusToast.setVisibility(show ? View.VISIBLE : View.GONE);
        if ((mCurrentRemaining > 0) && !mHideRemainingPhoto) {
            mRemainingPhotos.setVisibility(show ? View.GONE : View.VISIBLE);
        }
    }

    public void setHistogramEnabled(boolean enabled, CameraManager.CameraProxy camera) {
        mHistogramView.setVisibility(enabled ? View.VISIBLE : View.GONE);
        mHistogramView.setCamera(camera);
    }

    public void updateHistogramData(int[] data) {
        mHistogramView.updateData(data);
    }

    public void setOrientation(int orientation, boolean animation) {
        mOrientation = orientation;

        synchronized (mViews) {
            for (View v : mViews) {
                if (v instanceof RotateImageView) {
                    ((RotateImageView) v).setOrientation(orientation, animation);
                } else if (v instanceof HistogramView) {
                    ((HistogramView) v).setRotation(-orientation);
                }
            }
        }
        layoutRemaingPhotos();
    }

    public void hideCameraSettings() {
        for (int i = 0; i < mTopBar.getChildCount(); i++) {
            View v = mTopBar.getChildAt(i);
            if (v.getVisibility() == View.VISIBLE) {
                mTopBar.getChildAt(i).setVisibility(View.INVISIBLE);
            }
        }
    }

    public void showCameraSettings() {
        for (int i = 0; i < mTopBar.getChildCount(); i++) {
            View v = mTopBar.getChildAt(i);
            if (v.getVisibility() == View.INVISIBLE) {
                mTopBar.getChildAt(i).setVisibility(View.VISIBLE);
            }
        }
    }

    public void hideRemainingPhotoCnt() {
        mHideRemainingPhoto = true;
        mRemainingPhotos.setVisibility(View.GONE);
        mRemainingPhotosText.setVisibility(View.GONE);
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
