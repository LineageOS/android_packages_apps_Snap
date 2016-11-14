/*
 * Copyright (C) 2016 The CyanogenMod Project
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
package com.android.camera;

import android.graphics.Bitmap;
import android.graphics.Point;
import android.graphics.RectF;
import android.util.Log;
import android.util.Pair;
import android.view.View;
import android.view.ViewGroup;

import com.android.camera.ui.CameraControls;
import com.android.camera.ui.CaptureAnimationOverlay;
import com.android.camera.ui.ModuleSwitcher;
import com.android.camera.ui.RecordingTime;
import com.android.camera.util.CameraUtil;

import org.codeaurora.snapcam.R;

import java.util.ArrayList;
import java.util.List;

/**
 * we can start accumulating common code between UI classes here
 * toward an eventual unification - WF
 */
public abstract class BaseUI {

    private static final String TAG = "CAM_" + BaseUI.class.getSimpleName();

    protected final CaptureAnimationOverlay mCaptureOverlay;
    protected final View mPreviewCover;

    protected final CameraActivity mActivity;
    protected final ViewGroup mRootView;

    protected final CameraControls mCameraControls;
    protected final RecordingTime mRecordingTime;

    protected int mTopMargin = 0;
    protected int mBottomMargin = 0;
    protected int mScreenRatio = CameraUtil.RATIO_UNKNOWN;
    protected int mOrientation = 0;

    private boolean mOverlaysDisabled;
    private float mPreviewCoverAlpha;

    private final List<View> mDisabledViews = new ArrayList<>();

    public BaseUI(CameraActivity activity, ViewGroup rootView, int layout) {
        mActivity = activity;
        mRootView = rootView;

        mActivity.getLayoutInflater().inflate(layout, mRootView, true);

        mCameraControls = (CameraControls) mRootView.findViewById(R.id.camera_controls);
        mCaptureOverlay = (CaptureAnimationOverlay) mRootView.findViewById(R.id.capture_overlay);
        mPreviewCover = mRootView.findViewById(R.id.preview_cover);
        mRecordingTime = (RecordingTime) mRootView.findViewById(R.id.recording_time);

        Point size = new Point();
        mActivity.getWindowManager().getDefaultDisplay().getRealSize(size);
        mScreenRatio = CameraUtil.determineRatio(size.x, size.y);

        Pair<Integer, Integer> margins = CameraUtil.calculateMargins(mActivity);
        mTopMargin = margins.first;
        mBottomMargin = margins.second;
    }

    public void onPreviewFocusChanged(boolean previewFocused) {
        if (previewFocused) {
            showUI();
        } else {
            hideUI(true);
        }
    }

    public void showPreviewCover() {
        if (mPreviewCover != null) {
            synchronized (mPreviewCover) {
                if (mPreviewCover.getVisibility() != View.VISIBLE) {
                    mPreviewCover.setAlpha(0.0f);
                    mPreviewCover.setVisibility(View.VISIBLE);
                    disableOverlays();
                }
            }
        }
    }

    public void hidePreviewCover() {
        if (mPreviewCover != null) {
            synchronized (mPreviewCover) {
                if (mPreviewCover.getVisibility() != View.GONE) {
                    mPreviewCover.setAlpha(1.0f);
                    mPreviewCover.setVisibility(View.GONE);
                    enableOverlays();
                }
            }
        }
    }

    public void animateControls(float offset) {
        if (mPreviewCover != null) {
            synchronized (mPreviewCover) {
                setPreviewCoverAlpha(offset, false);
                if (mCameraControls != null) {
                    mCameraControls.setUIOffset(offset, true);
                }
            }
        }
    }

    public boolean isPreviewCoverVisible() {
        if (mPreviewCover == null) {
            return false;
        }
        synchronized (mPreviewCover) {
            return mPreviewCover.getVisibility() == View.VISIBLE;
        }
    }

    public void hideUI() {
        hideUI(false);
    }

    protected void hideUI(boolean toBlack) {
        if (mPreviewCover != null) {
            synchronized (mPreviewCover) {
                if (toBlack) {
                    setPreviewCoverAlpha(1.0f, true);
                } else {
                    setPreviewCoverAlpha(0.4f, true);
                }
                if (mCameraControls != null) {
                    mCameraControls.hideUI(toBlack);
                }
            }
        }
    }

    protected void showUI() {
        if (mPreviewCover != null) {
            synchronized (mPreviewCover) {
                setPreviewCoverAlpha(0.0f, true);
                if (mCameraControls != null) {
                    mCameraControls.showUI();
                }
            }
        }
    }

    public boolean arePreviewControlsVisible() {
        return mCameraControls != null && mCameraControls.arePreviewControlsVisible();
    }

    public void hideSwitcher() {
        if (mCameraControls != null) {
            mCameraControls.hideSwitcher();
        }
    }

    public void showSwitcher() {
        if (mCameraControls != null) {
            mCameraControls.showSwitcher();
        }
    }

    public void removeControlView(View v) {
        if (mCameraControls != null) {
            mCameraControls.removeFromViewList(v);
        }
    }

    public void setSwitcherIndex() {
        int module = ModuleSwitcher.PHOTO_MODULE_INDEX;
        if (this instanceof WideAnglePanoramaUI) {
            module = ModuleSwitcher.WIDE_ANGLE_PANO_MODULE_INDEX;
        } else if (this instanceof VideoUI) {
            module = ModuleSwitcher.VIDEO_MODULE_INDEX;
        } else if (this instanceof CaptureUI) {
            module = ModuleSwitcher.CAPTURE_MODULE_INDEX;
        }
        mCameraControls.setModuleIndex(module);
    }

    public void animateFlash(boolean shortFlash) {
        if (mCaptureOverlay != null) {
            mCaptureOverlay.startFlashAnimation(shortFlash);
        }
        mActivity.updateThumbnail(true);
    }

    public void animateFlash(Bitmap bitmap) {
        if (mCaptureOverlay != null) {
            mCaptureOverlay.startFlashAnimation(false);
        }
        mActivity.updateThumbnail(bitmap);
    }

    protected void onPreviewRectChanged(RectF rect) {
        CameraUtil.dumpRect(rect, "onPreviewRectChanged");

        if (mCaptureOverlay != null) {
            mCaptureOverlay.setPreviewRect(rect);
        }
        if (mCameraControls != null) {
            mCameraControls.setPreviewRect(rect);
        }
    }

    private void enableOverlays() {
        synchronized (mDisabledViews) {
            if (!mOverlaysDisabled) {
                return;
            }

            for (View v : mDisabledViews) {
                v.setVisibility(View.VISIBLE);
            }

            mDisabledViews.clear();
            mOverlaysDisabled = false;
        }
    }

    private void disableOverlays() {
        synchronized (mDisabledViews) {
            if (mOverlaysDisabled) {
                return;
            }

            mOverlaysDisabled = true;

            View focusRingView = mRootView.findViewById(R.id.focus_ring);
            if (focusRingView != null && focusRingView.getVisibility() == View.VISIBLE) {
                focusRingView.setVisibility(View.GONE);
                mDisabledViews.add(focusRingView);
            }

            View faceView = mRootView.findViewById(R.id.face_view);
            if (faceView != null && faceView.getVisibility() == View.VISIBLE) {
                faceView.setVisibility(View.GONE);
                mDisabledViews.add(faceView);
            }
        }
    }

    public void setOrientation(int orientation, boolean animation) {
        mOrientation = orientation;

        if (mCameraControls != null) {
            mCameraControls.setOrientation(orientation, animation);
        }
        if (mRecordingTime != null) {
            mRecordingTime.setOrientation(orientation);
        }
    }

    public void startRecordingTimer(int frameRate, long frameInterval, long durationMs) {
        if (mRecordingTime != null) {
            mRecordingTime.start(frameRate, frameInterval, durationMs);
        }
    }

    public void stopRecordingTimer() {
        if (mRecordingTime != null) {
            mRecordingTime.stop();
        }
    }

    public long getRecordingTime() {
        if (mRecordingTime != null) {
            return mRecordingTime.getTime();
        }
        return -1;
    }

    public void showTimeLapseUI(boolean enable) {
        if (mRecordingTime != null) {
            mRecordingTime.showTimeLapse(enable);
        }
    }

    private void setPreviewCoverAlpha(float alpha, boolean animate) {
        if (mPreviewCover == null) {
            return;
        }
        synchronized (mPreviewCover) {
            if (alpha == mPreviewCoverAlpha || alpha < 0.0f || alpha > 1.0f) {
                return;
            }

            if (alpha == 0.0f) {
                hidePreviewCover();
            } else {
                showPreviewCover();
            }

            if (animate) {
                mPreviewCover.animate().cancel();
                mPreviewCover.animate().alpha(alpha).start();
            } else {
                mPreviewCover.setAlpha(alpha);
            }

            mPreviewCoverAlpha = alpha;
        }
    }
}
