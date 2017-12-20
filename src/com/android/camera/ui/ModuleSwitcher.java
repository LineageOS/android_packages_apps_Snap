/*
 * Copyright (C) 2012 The Android Open Source Project
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

import android.app.Activity;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Point;
import android.graphics.drawable.ColorDrawable;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.PopupWindow;

import com.android.camera.util.CameraUtil;
import com.android.camera.util.PhotoSphereHelper;
import com.android.camera.util.UsageStatistics;

import org.codeaurora.snapcam.R;

public class ModuleSwitcher extends RotateImageView {

    @SuppressWarnings("unused")
    private static final String TAG = "CAM_Switcher";
    private static final int SWITCHER_POPUP_ANIM_DURATION = 200;

    public static final int PHOTO_MODULE_INDEX = 0;
    public static final int VIDEO_MODULE_INDEX = 1;
    public static final int WIDE_ANGLE_PANO_MODULE_INDEX = 2;
    public static final int QR_MODULE_INDEX = 3;
    public static final int LIGHTCYCLE_MODULE_INDEX = 4;
    public static final int GCAM_MODULE_INDEX = 5;
    public static final int CAPTURE_MODULE_INDEX = 6;

    private static final int[][] DRAW_AND_DESC_IDS = {
        { R.drawable.ic_switch_camera, R.string.accessibility_switch_to_camera },
        { R.drawable.ic_switch_video, R.string.accessibility_switch_to_video },
        { R.drawable.ic_switch_pan, R.string.accessibility_switch_to_panorama },
        { R.drawable.ic_cam_switcher_qr, R.string.accessibility_switch_to_qr },
        { R.drawable.ic_switch_photosphere, R.string.accessibility_switch_to_photo_sphere },
        { R.drawable.ic_switch_gcam, R.string.accessibility_switch_to_gcam }
    };

    public interface ModuleSwitchListener {
        public void onModuleSelected(int i, Point hotspot);

        public void onShowSwitcherPopup();
    }

    private ModuleSwitchListener mListener;
    private int mCurrentIndex;
    private int[] mModuleIds;
    private int[][] mDrawAndDescIds;
    private PopupWindow mPopup;
    private boolean mShowingPopup;
    private LinearLayout mContent;

    private float mTranslationX;
    private float mTranslationY;

    public ModuleSwitcher(Context context) {
        super(context);
        init(context);
    }

    public ModuleSwitcher(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    private void init(Context context) {
        initializeDrawables(context);
        initPopup();
    }

    public void initializeDrawables(Context context) {
        int numDrawIds = DRAW_AND_DESC_IDS.length;

        if (!PhotoSphereHelper.hasLightCycleCapture(context)) {
            --numDrawIds;
        }

        // Always decrement one because of GCam.
        --numDrawIds;

        mDrawAndDescIds = new int[numDrawIds][2];
        mModuleIds = new int[numDrawIds];
        int index = 0;
        for (int i = 0; i < DRAW_AND_DESC_IDS.length; i++) {
            if (i == LIGHTCYCLE_MODULE_INDEX && !PhotoSphereHelper.hasLightCycleCapture(context)) {
                continue; // not enabled, so don't add to UI
            }
            if (i == GCAM_MODULE_INDEX) {
                continue; // don't add to UI
            }
            mModuleIds[index] = i;
            mDrawAndDescIds[index++] = DRAW_AND_DESC_IDS[i];
        }
    }

    public void setCurrentIndex(int i) {
        mCurrentIndex = i;
        if (i == GCAM_MODULE_INDEX || i == CAPTURE_MODULE_INDEX) {
          setImageResource(R.drawable.ic_switch_camera);
        } else {
          setImageResource(mDrawAndDescIds[i][0]);
        }
    }

    public void setSwitchListener(ModuleSwitchListener l) {
        mListener = l;
    }

    public void showPopup() {
        showSwitcher();
        mListener.onShowSwitcherPopup();
    }

    private void onModuleSelected(int ix, Point hotspot) {
        closePopup();
        if ((ix != mCurrentIndex) && (mListener != null)) {
            UsageStatistics.onEvent("CameraModeSwitch", null, null);
            UsageStatistics.setPendingTransitionCause(
                    UsageStatistics.TRANSITION_MENU_TAP);
            setCurrentIndex(ix);
            mListener.onModuleSelected(mModuleIds[ix], hotspot);
        }
    }

    private PopupWindow getPopup() {
        PopupWindow popup = new PopupWindow(mContent);
        popup.setHeight(WindowManager.LayoutParams.WRAP_CONTENT);
        popup.setWidth(WindowManager.LayoutParams.WRAP_CONTENT);
        // Closes the popup window when touch outside of it - when looses focus
        popup.setOutsideTouchable(true);
        popup.setFocusable(true);
        popup.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        popup.setAnimationStyle(android.R.style.Animation_Dialog);
        return popup;
    }

    private void initPopup() {
        final LayoutInflater inflater = LayoutInflater.from(getContext());
        mContent = (LinearLayout) inflater.inflate(R.layout.switcher_popup, null);

        for (int i = mDrawAndDescIds.length - 1; i >= 0; i--) {
            RotateImageView item = (RotateImageView)
                    inflater.inflate(R.layout.switcher_popup_item, mContent, false);
            item.setImageResource(mDrawAndDescIds[i][0]);
            item.setContentDescription(getContext().getString(mDrawAndDescIds[i][1]));
            final int index = i;
            item.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (mPopup != null) {
                        int[] loc = new int[2];
                        v.getLocationOnScreen(loc);
                        onModuleSelected(index, new Point(loc[0], loc[1]));
                    }
                }
            });
            mContent.addView(item);
        }
        mContent.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED);
    }

    @Override
    public void onConfigurationChanged(Configuration config) {
        closePopup();
    }

    public boolean showsPopup() {
        return mShowingPopup;
    }

    private void showSwitcher() {
        mShowingPopup = true;
        mPopup = getPopup();
        mPopup.showAsDropDown(this, ((getWidth() / 2) - (mContent.getMeasuredWidth() / 2)),
                -(mContent.getMeasuredHeight() + Math.round(0.75f * getHeight())),
                Gravity.TOP);
    }

    public void closePopup() {
        mShowingPopup = false;
        if (mPopup != null) {
            mPopup.dismiss();
            mPopup = null;
        }
    }

    @Override
    public void setOrientation(int degree, boolean animate) {
        super.setOrientation(degree, animate);
        if (mPopup == null) {
            return;
        }
        ViewGroup content = (ViewGroup) mPopup.getContentView();
        if (content == null) {
            return;
        }
        for (int i = 0; i < content.getChildCount(); i++) {
            RotateImageView iv = (RotateImageView) content.getChildAt(i);
            iv.setOrientation(degree, animate);
        }
    }

    private void layoutPopup() {
        if (mContent == null) {
            return;
        }

        int orientation = CameraUtil.getDisplayRotation((Activity) getContext());
        int w = mContent.getMeasuredWidth();
        int h = mContent.getMeasuredHeight();

        if (orientation == 0) {
            mContent.layout(getRight() - w, getBottom() - h, getRight(), getBottom());
            mTranslationX = 0;
            mTranslationY = h / 3;
        } else if (orientation == 90) {
            mTranslationX = w / 3;
            mTranslationY = -h / 3;
            mContent.layout(getRight() - w, getTop(), getRight(), getTop() + h);
        } else if (orientation == 180) {
            mTranslationX = -w / 3;
            mTranslationY = -h / 3;
            mContent.layout(getLeft(), getTop(), getLeft() + w, getTop() + h);
        } else {
            mTranslationX = -w / 3;
            mTranslationY = h - getHeight();
            mContent.layout(getLeft(), getBottom() - h, getLeft() + w, getBottom());
        }
    }

    @Override
    public void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        layoutPopup();
    }
}
