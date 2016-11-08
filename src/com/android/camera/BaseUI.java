package com.android.camera;

import android.view.View;

/** we can start accumulating common code between UI classes here
 *  toward an eventual unification - WF */
public abstract class BaseUI {
    protected View mPreviewCover;

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
}
