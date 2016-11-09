package com.android.camera;

import android.view.View;

import com.android.camera.ui.CameraControls;
import com.android.camera.ui.ModuleSwitcher;

/** we can start accumulating common code between UI classes here
 *  toward an eventual unification - WF */
public abstract class BaseUI {
    protected View mPreviewCover;

    protected CameraControls mCameraControls;

    public void showPreviewCover() {
        if (mPreviewCover != null && mPreviewCover.getVisibility() != View.VISIBLE) {
            mPreviewCover.setVisibility(View.VISIBLE);
        }
    }

    public void hidePreviewCover() {
        if (mPreviewCover != null && mPreviewCover.getVisibility() != View.GONE) {
            mPreviewCover.setVisibility(View.GONE);
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
}
