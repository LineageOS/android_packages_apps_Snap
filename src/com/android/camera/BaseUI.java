package com.android.camera;

import android.graphics.Point;
import android.graphics.RectF;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewStub;

import com.android.camera.ui.CameraControls;
import com.android.camera.ui.CaptureAnimationOverlay;
import com.android.camera.ui.ModuleSwitcher;
import com.android.camera.util.CameraUtil;

import org.codeaurora.snapcam.R;

import java.util.ArrayList;
import java.util.List;

/** we can start accumulating common code between UI classes here
 *  toward an eventual unification - WF */
public abstract class BaseUI {

    protected final CameraControls mCameraControls;
    protected final CaptureAnimationOverlay mCaptureOverlay;
    protected final View mPreviewCover;

    protected final CameraActivity mActivity;
    protected final ViewGroup mRootView;

    protected int mTopMargin = 0;
    protected int mBottomMargin = 0;
    protected int mScreenRatio = CameraUtil.RATIO_UNKNOWN;

    private boolean mOverlaysDisabled;
    private final List<View> mDisabledViews = new ArrayList<>();

    public BaseUI(CameraActivity activity, ViewGroup rootView, int layout) {
        mActivity = activity;
        mRootView = rootView;

        mActivity.getLayoutInflater().inflate(layout, mRootView, true);

        mCameraControls = (CameraControls) mRootView.findViewById(R.id.camera_controls);
        mCaptureOverlay = (CaptureAnimationOverlay) mRootView.findViewById(R.id.capture_overlay);
        mPreviewCover = mRootView.findViewById(R.id.preview_cover);

        Point size = new Point();
        mActivity.getWindowManager().getDefaultDisplay().getRealSize(size);
        mScreenRatio = CameraUtil.determineRatio(size.x, size.y);
        calculateMargins(size);
        mCameraControls.setMargins(mTopMargin, mBottomMargin);
    }

    private void calculateMargins(Point size) {
        int l = size.x > size.y ? size.x : size.y;
        int tm = mActivity.getResources().getDimensionPixelSize(R.dimen.preview_top_margin);
        int bm = mActivity.getResources().getDimensionPixelSize(R.dimen.preview_bottom_margin);
        mTopMargin = l / 4 * tm / (tm + bm);
        mBottomMargin = l / 4 - mTopMargin;
    }

    public void showPreviewCover() {
        if (mPreviewCover != null && mPreviewCover.getVisibility() != View.VISIBLE) {
            mPreviewCover.setVisibility(View.VISIBLE);
            disableOverlays();
        }
    }

    public void hidePreviewCover() {
        if (mPreviewCover != null && mPreviewCover.getVisibility() != View.GONE) {
            mPreviewCover.setVisibility(View.GONE);
            enableOverlays();
        }
    }

    public void setPreviewCoverAlpha(float alpha) {
        if (mPreviewCover != null) {
            mPreviewCover.setAlpha(alpha);
        }
    }

    public boolean isPreviewCoverVisible() {
        return mPreviewCover != null && mPreviewCover.getVisibility() == View.VISIBLE;
    }

    public void hideUI() {
        hideUI(false);
    }

    protected void hideUI(boolean toBlack) {
        if (mCameraControls != null) {
            mCameraControls.hideUI(toBlack);
        }
    }

    protected void showUI() {
        if (mCameraControls != null) {
            mCameraControls.showUI();
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
    }

    protected void onPreviewRectChanged(RectF rect) {
        mCaptureOverlay.setPreviewRect(rect);
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
}
