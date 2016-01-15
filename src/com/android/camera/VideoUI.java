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

package com.android.camera;

import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.RectF;
import android.graphics.drawable.ColorDrawable;
import android.hardware.Camera;
import android.hardware.Camera.Face;
import android.hardware.Camera.Parameters;
import android.util.Log;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnLayoutChangeListener;
import android.view.ViewGroup;
import android.view.ViewStub;
import android.widget.FrameLayout;
import android.widget.FrameLayout.LayoutParams;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.PopupWindow;

import com.android.camera.CameraManager.CameraProxy;
import com.android.camera.CameraPreference.OnPreferenceChangedListener;
import com.android.camera.PhotoUI.SurfaceTextureSizeChangedListener;
import com.android.camera.ui.AbstractSettingPopup;
import com.android.camera.ui.CameraRootView;
import com.android.camera.ui.FaceView;
import com.android.camera.ui.ListSubMenu;
import com.android.camera.ui.PieRenderer;
import com.android.camera.ui.RenderOverlay;
import com.android.camera.ui.RotateImageView;
import com.android.camera.ui.RotateLayout;
import com.android.camera.ui.RotateTextToast;
import com.android.camera.ui.ZoomRenderer;
import com.android.camera.ui.focus.FocusRing;
import com.android.camera.util.CameraUtil;

import org.codeaurora.snapcam.R;

import java.util.List;

public class VideoUI extends BaseUI implements PieRenderer.PieListener,
        PreviewGestures.SingleTapListener,
        CameraRootView.MyDisplayListener,
        SurfaceHolder.Callback,
        PauseButton.OnPauseButtonListener,
        CameraManager.CameraFaceDetectionCallback{
    private static final String TAG = "CAM_VideoUI";
    // module fields
    private final FocusRing mFocusRing;
    private SurfaceHolder mSurfaceHolder;
    // An review image having same size as preview. It is displayed when
    // recording is stopped in capture intent.
    private ImageView mReviewImage;
    private View mReviewCancelButton;
    private View mReviewDoneButton;
    private View mReviewPlayButton;
    private ShutterButton mShutterButton;
    private RenderOverlay mRenderOverlay;
    private PieRenderer mPieRenderer;
    private VideoMenu mVideoMenu;
    private SettingsPopup mPopup;
    private ZoomRenderer mZoomRenderer;
    private PreviewGestures mGestures;
    private View mMenuButton;
    private boolean mRecordingStarted = false;
    private VideoController mController;
    private int mZoomMax;
    private List<Integer> mZoomRatios;
    private ImageView mThumbnail;
    private boolean mOrientationResize;
    private boolean mPrevOrientationResize;
    private RotateLayout mMenuLayout;
    private RotateLayout mSubMenuLayout;
    private LinearLayout mPreviewMenuLayout;

    private SurfaceView mSurfaceView = null;
    private int mMaxPreviewWidth = 0;
    private int mMaxPreviewHeight = 0;
    private float mAspectRatio = 4f / 3f;
    private boolean mAspectRatioResize;
    private int mPreviewOrientation = -1;
    private int mOrientation;

    private RotateImageView mMuteButton;

    //Face detection
    private FaceView mFaceView;
    private SurfaceTextureSizeChangedListener mSurfaceTextureSizeListener;
    private float mSurfaceTextureUncroppedWidth;
    private float mSurfaceTextureUncroppedHeight;

    public enum SURFACE_STATUS {
        HIDE,
        SURFACE_VIEW;
    }

    private class SettingsPopup extends PopupWindow {
        public SettingsPopup(View popup) {
            super(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
            setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            setOutsideTouchable(true);
            setFocusable(true);
            popup.setVisibility(View.VISIBLE);
            setContentView(popup);
            showAtLocation(mRootView, Gravity.CENTER, 0, 0);
        }

        public void dismiss(boolean topLevelOnly) {
            super.dismiss();
            popupDismissed();
            showUI();
            // mVideoMenu.popupDismissed(topLevelOnly);

            // Switch back into fullscreen/lights-out mode after popup
            // is dimissed.
            mActivity.setSystemBarsVisibility(false);
        }

        @Override
        public void dismiss() {
            // Called by Framework when touch outside the popup or hit back key
            dismiss(true);
        }
    }

    public synchronized void applySurfaceChange(SURFACE_STATUS status) {
        if(status == SURFACE_STATUS.HIDE) {
            mSurfaceView.setVisibility(View.GONE);
            return;
        }
        mSurfaceView.setVisibility(View.VISIBLE);
    }

    public VideoUI(CameraActivity activity, VideoController controller, ViewGroup parent) {
        super(activity, parent, R.layout.video_module);
        mController = controller;
        // display the view
        mSurfaceView = (SurfaceView) mRootView.findViewById(R.id.mdp_preview_content);
        mSurfaceView.setVisibility(View.VISIBLE);
        mSurfaceHolder = mSurfaceView.getHolder();
        mSurfaceHolder.addCallback(this);
        mSurfaceHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
        Log.v(TAG, "Using mdp_preview_content (MDP path)");
        View surfaceContainer = mRootView.findViewById(R.id.preview_container);
        surfaceContainer.addOnLayoutChangeListener(new OnLayoutChangeListener() {
            @Override
            public void onLayoutChange(View v, int left, int top, int right,
                    int bottom, int oldLeft, int oldTop, int oldRight,
                    int oldBottom) {
                int width = right - left;
                int height = bottom - top;

                tryToCloseSubList();
                if (mMaxPreviewWidth == 0 && mMaxPreviewHeight == 0) {
                    mMaxPreviewWidth = width;
                    mMaxPreviewHeight = height;
                }

                int orientation = mActivity.getResources().getConfiguration().orientation;
                if ((orientation == Configuration.ORIENTATION_PORTRAIT && width > height)
                        || (orientation == Configuration.ORIENTATION_LANDSCAPE && width < height)) {
                    // The screen has rotated; swap SurfaceView width & height
                    // to ensure correct preview
                    int oldWidth = width;
                    width = height;
                    height = oldWidth;
                    Log.d(TAG, "Swapping SurfaceView width & height dimensions");
                    if (mMaxPreviewWidth != 0 && mMaxPreviewHeight != 0) {
                        int temp = mMaxPreviewWidth;
                        mMaxPreviewWidth = mMaxPreviewHeight;
                        mMaxPreviewHeight = temp;
                    }
                }
                if (mOrientationResize != mPrevOrientationResize
                        || mAspectRatioResize) {
                    layoutPreview(mAspectRatio);
                    mAspectRatioResize = false;
                }
            }
        });

        mRenderOverlay = (RenderOverlay) mRootView.findViewById(R.id.render_overlay);
        mFocusRing = (FocusRing) mRootView.findViewById(R.id.focus_ring);
        mShutterButton = (ShutterButton) mRootView.findViewById(R.id.shutter_button);

        mMuteButton = (RotateImageView)mRootView.findViewById(R.id.mute_button);
        mMuteButton.setVisibility(View.VISIBLE);
        if(!((VideoModule)mController).isAudioMute()) {
            mMuteButton.setImageResource(R.drawable.ic_unmuted_button);
        } else {
            mMuteButton.setImageResource(R.drawable.ic_muted_button);
        }
        mMuteButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                boolean isEnabled = !((VideoModule)mController).isAudioMute();
                ((VideoModule)mController).setMute(isEnabled, true);
                if(!isEnabled)
                    mMuteButton.setImageResource(R.drawable.ic_unmuted_button);
                else
                    mMuteButton.setImageResource(R.drawable.ic_muted_button);
            }
        });

        initializeMiscControls();
        initializeControlByIntent();

        mRecordingTime.setPauseListener(this);

        ViewStub faceViewStub = (ViewStub) mRootView
                .findViewById(R.id.face_view_stub);
        if (faceViewStub != null) {
            faceViewStub.inflate();
            mFaceView = (FaceView) mRootView.findViewById(R.id.face_view);
            setSurfaceTextureSizeChangedListener(mFaceView);
        }
        mOrientationResize = false;
        mPrevOrientationResize = false;
    }

    public void cameraOrientationPreviewResize(boolean orientation){
       mPrevOrientationResize = mOrientationResize;
       mOrientationResize = orientation;
    }

    public void setSurfaceTextureSizeChangedListener(SurfaceTextureSizeChangedListener listener) {
        mSurfaceTextureSizeListener = listener;
    }

    private void initializeControlByIntent() {
        mMenuButton = mRootView.findViewById(R.id.menu);
        mMenuButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                mVideoMenu.openFirstLevel();
            }
        });

        if (mController.isVideoCaptureIntent()) {
            hideSwitcher();
            mActivity.getLayoutInflater().inflate(R.layout.review_module_control,
                    (ViewGroup) mCameraControls);
            // Cannot use RotateImageView for "done" and "cancel" button because
            // the tablet layout uses RotateLayout, which cannot be cast to
            // RotateImageView.
            mReviewDoneButton = mRootView.findViewById(R.id.btn_done);
            mReviewCancelButton = mRootView.findViewById(R.id.btn_cancel);
            mReviewPlayButton = mRootView.findViewById(R.id.btn_play);
            mReviewCancelButton.setVisibility(View.VISIBLE);
            mReviewDoneButton.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    mController.onReviewDoneClicked(v);
                }
            });
            mReviewCancelButton.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    mController.onReviewCancelClicked(v);
                }
            });
            mReviewPlayButton.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    mController.onReviewPlayClicked(v);
                }
            });
        }
    }

    public void setPreviewSize(int width, int height) {
        if (width == 0 || height == 0) {
            Log.w(TAG, "Preview size should not be 0.");
            return;
        }
        float ratio;
        if (width > height) {
            ratio = (float) width / height;
        } else {
            ratio = (float) height / width;
        }
        if (mOrientationResize && CameraUtil.isScreenRotated(mActivity)) {
            ratio = 1 / ratio;
        }

        if (ratio != mAspectRatio){
            mAspectRatioResize = true;
            mAspectRatio = ratio;
        }

        layoutPreview((float) ratio);
    }

    private void layoutPreview(float ratio) {
        FrameLayout.LayoutParams lp = null;

        float scaledTextureWidth = 0.0f, scaledTextureHeight = 0.0f;
        int rotation = CameraUtil.getDisplayRotation(mActivity);
        mScreenRatio = CameraUtil.determineRatio(ratio);
        if (mScreenRatio == CameraUtil.RATIO_16_9
                && CameraUtil.determinCloseRatio(ratio) == CameraUtil.RATIO_4_3) {
            int l = (mTopMargin + mBottomMargin) * 4;
            int s = l * 9 / 16;
            switch (rotation) {
                case 90:
                    lp = new FrameLayout.LayoutParams(l * 3 / 4, s);
                    lp.setMargins(mTopMargin, 0, mBottomMargin, 0);
                    scaledTextureWidth = l * 3 / 4;
                    scaledTextureHeight = s;
                    break;
                case 180:
                    lp = new FrameLayout.LayoutParams(s, l * 3 / 4);
                    lp.setMargins(0, mBottomMargin, 0, mTopMargin);
                    scaledTextureWidth = s;
                    scaledTextureHeight = l * 3 / 4;
                    break;
                case 270:
                    lp = new FrameLayout.LayoutParams(l * 3 / 4, s);
                    lp.setMargins(mBottomMargin, 0, mTopMargin, 0);
                    scaledTextureWidth = l * 3 / 4;
                    scaledTextureHeight = s;
                    break;
                default:
                    lp = new FrameLayout.LayoutParams(s, l * 3 / 4);
                    lp.setMargins(0, mTopMargin, 0, mBottomMargin);
                    scaledTextureWidth = s;
                    scaledTextureHeight = l * 3 / 4;
                    break;
            }
        } else {
            float width = mMaxPreviewWidth, height = mMaxPreviewHeight;
            if (width == 0 || height == 0) return;
            if(mScreenRatio == CameraUtil.RATIO_4_3)
                height -=  (mTopMargin + mBottomMargin);
            if (mOrientationResize) {
                scaledTextureWidth = height * mAspectRatio;
                if (scaledTextureWidth > width) {
                    scaledTextureWidth = width;
                    scaledTextureHeight = scaledTextureWidth / mAspectRatio;
                } else {
                    scaledTextureHeight = height;
                }
            } else {
                if (width > height) {
                    if(Math.max(width, height * mAspectRatio) > width) {
                        scaledTextureWidth = width;
                        scaledTextureHeight = width / mAspectRatio;
                    } else {
                        scaledTextureWidth = height * mAspectRatio;
                        scaledTextureHeight = height;
                    }
                } else {
                    if(Math.max(height, width * mAspectRatio) > height) {
                        scaledTextureWidth = height / mAspectRatio;
                        scaledTextureHeight = height;
                    } else {
                        scaledTextureWidth = width;
                        scaledTextureHeight = width * mAspectRatio;
                    }
                }
            }

            Log.v(TAG, "setTransformMatrix: scaledTextureWidth = " + scaledTextureWidth
                    + ", scaledTextureHeight = " + scaledTextureHeight);

            if (((rotation == 0 || rotation == 180) && scaledTextureWidth > scaledTextureHeight)
                    || ((rotation == 90 || rotation == 270)
                        && scaledTextureWidth < scaledTextureHeight)) {
                lp = new FrameLayout.LayoutParams((int) scaledTextureHeight,
                        (int) scaledTextureWidth, Gravity.CENTER);
            } else {
                lp = new FrameLayout.LayoutParams((int) scaledTextureWidth,
                        (int) scaledTextureHeight, Gravity.CENTER);
            }
        }

        if (mSurfaceTextureUncroppedWidth != scaledTextureWidth ||
                mSurfaceTextureUncroppedHeight != scaledTextureHeight) {
            mSurfaceTextureUncroppedWidth = scaledTextureWidth;
            mSurfaceTextureUncroppedHeight = scaledTextureHeight;
            if (mSurfaceTextureSizeListener != null) {
                mSurfaceTextureSizeListener.onSurfaceTextureSizeChanged(
                        (int) mSurfaceTextureUncroppedWidth,
                        (int) mSurfaceTextureUncroppedHeight);
                Log.d(TAG, "mSurfaceTextureUncroppedWidth=" + mSurfaceTextureUncroppedWidth
                        + "mSurfaceTextureUncroppedHeight=" + mSurfaceTextureUncroppedHeight);
            }
        }


        if(lp != null) {
            mSurfaceView.setLayoutParams(lp);
            mSurfaceView.requestLayout();
            if (mFaceView != null) {
                mFaceView.setLayoutParams(lp);
            }

        }

        if (scaledTextureWidth > 0 && scaledTextureHeight > 0) {
            mController.onScreenSizeChanged((int) scaledTextureWidth,
                    (int) scaledTextureHeight);
        }
    }

    public boolean collapseCameraControls() {
        boolean ret = false;
        mCameraControls.collapse();
        if (mVideoMenu != null) {
            mVideoMenu.closeAllView();
        }
        if (mPopup != null) {
            dismissPopup(false);
            ret = true;
        }
        return ret;
    }

    public boolean removeTopLevelPopup() {
        if (mPopup != null) {
            dismissPopup(true);
            return true;
        }
        return false;
    }

    public void enableCameraControls(boolean enable) {
        if (mGestures != null) {
            mGestures.setZoomOnly(!enable);
        }
        if (mPieRenderer != null && mPieRenderer.showsItems()) {
            mPieRenderer.hide();
        }
    }

    public void initDisplayChangeListener() {
        ((CameraRootView) mRootView).setDisplayChangeListener(this);
    }

    public void setDisplayOrientation(int orientation) {
        if (mFaceView != null) {
             mFaceView.setDisplayOrientation(orientation);
        }

        if ((mPreviewOrientation == -1 || mPreviewOrientation != orientation)
                && mVideoMenu != null && mVideoMenu.isPreviewMenuBeingShown()) {
            dismissSceneModeMenu();
            mVideoMenu.addModeBack();
        }
        mPreviewOrientation = orientation;
    }

    public void removeDisplayChangeListener() {
        ((CameraRootView) mRootView).removeDisplayChangeListener();
    }

// no customvideo?
    public void overrideSettings(final String... keyvalues) {
        if (mVideoMenu != null) {
            mVideoMenu.overrideSettings(keyvalues);
        }
    }

    public SurfaceHolder getSurfaceHolder() {
        return mSurfaceHolder;
    }

    public void hideSurfaceView() {
        mSurfaceView.setVisibility(View.INVISIBLE);
    }

    public void showSurfaceView() {
        mSurfaceView.setVisibility(View.VISIBLE);
    }

    public void onCameraOpened(PreferenceGroup prefGroup, ComboPreferences prefs,
                               Camera.Parameters params, OnPreferenceChangedListener listener) {
        if (mPieRenderer == null) {
            mPieRenderer = new PieRenderer(mActivity);
            // mVideoMenu = new VideoMenu(mActivity, this, mPieRenderer);
            mPieRenderer.setPieListener(this);
            mRenderOverlay.addRenderer(mPieRenderer);
        }
        if (mVideoMenu == null) {
            mVideoMenu = new VideoMenu(mActivity, this);
            mVideoMenu.setListener(listener);
        }
        mVideoMenu.initialize(prefGroup);

        if (mZoomRenderer == null) {
            mZoomRenderer = new ZoomRenderer(mActivity);
            mRenderOverlay.addRenderer(mZoomRenderer);
        }

        if (mGestures == null) {
            mGestures = new PreviewGestures(mActivity, this, mZoomRenderer, mPieRenderer);
            mRenderOverlay.setGestures(mGestures);
        }
        mGestures.setVideoMenu(mVideoMenu);

        mGestures.setZoomEnabled(params.isZoomSupported());
        mGestures.setRenderOverlay(mRenderOverlay);
        mRenderOverlay.requestLayout();

        initializeZoom(params);
        mActivity.setPreviewGestures(mGestures);

        if (!mActivity.isSecureCamera()) {
            mThumbnail = (ImageView) mRootView.findViewById(R.id.preview_thumb);
            mThumbnail.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    // Do not allow navigation to filmstrip during video recording
                    if (!mRecordingStarted && !isCameraControlsAnimating()) {
                        mActivity.gotoGallery();
                    }
                }
            });
        }
    }

    public boolean isCameraControlsAnimating() {
        return mCameraControls.isAnimating();
    }

    public void setPrefChangedListener(OnPreferenceChangedListener listener) {
        mVideoMenu.setListener(listener);
    }

    // called from onResume but only the first time
    public void initializeFirstTime() {
        // Initialize shutter button.
        mShutterButton.setImageResource(R.drawable.btn_new_shutter_video);
        mShutterButton.addOnShutterButtonListener(mController);
        mShutterButton.setVisibility(View.VISIBLE);

        mCameraControls.disableSceneModes();
    }

    private void initializeMiscControls() {
        mReviewImage = (ImageView) mRootView.findViewById(R.id.review_image);
        mShutterButton.requestFocus();
        mShutterButton.enableTouch(true);
    }

    public void setAspectRatio(double ratio) {
        if (mOrientationResize && CameraUtil.isScreenRotated(mActivity)) {
            ratio = 1 / ratio;
        }

        if (ratio != mAspectRatio) {
            mAspectRatioResize = true;
            mAspectRatio = (float)ratio;
        }

        layoutPreview((float)ratio);
    }

    public void dismissPopup(boolean topLevelOnly) {
        // In review mode, we do not want to bring up the camera UI
        if (mController.isInReviewMode()) return;
        if (mPopup != null) {
            mPopup.dismiss(topLevelOnly);
        }
    }

    public boolean is4KEnabled() {
        if(mController != null)
            return ((VideoModule)mController).is4KEnabled();
        else
            return false;
    }

    private void popupDismissed() {
        mPopup = null;
    }

    public boolean onBackPressed() {
        if (mVideoMenu != null && mVideoMenu.handleBackKey()) {
            return true;
        }
        if (hidePieRenderer()) {
            return true;
        } else {
            return removeTopLevelPopup();
        }
    }

    public void cleanupListview() {
        showUI();
        mActivity.setSystemBarsVisibility(false);
    }

    public void dismissLevel1() {
        if (mMenuLayout != null) {
            ((ViewGroup) mRootView).removeView(mMenuLayout);
            mMenuLayout = null;
        }
    }

    public void dismissLevel2() {
        if (mSubMenuLayout != null) {
            ((ViewGroup) mRootView).removeView(mSubMenuLayout);
            mSubMenuLayout = null;
        }
    }

    public boolean sendTouchToPreviewMenu(MotionEvent ev) {
        return mPreviewMenuLayout.dispatchTouchEvent(ev);
    }

    public boolean sendTouchToMenu(MotionEvent ev) {
        if (mMenuLayout != null) {
            View v = mMenuLayout.getChildAt(0);
            return v.dispatchTouchEvent(ev);
        }
        return false;
    }

    public void dismissSceneModeMenu() {
        if (mPreviewMenuLayout != null) {
            ((ViewGroup) mRootView).removeView(mPreviewMenuLayout);
            mPreviewMenuLayout = null;
        }
    }

    public void removeSceneModeMenu() {
        if (mPreviewMenuLayout != null) {
            ((ViewGroup) mRootView).removeView(mPreviewMenuLayout);
            mPreviewMenuLayout = null;
        }
        cleanupListview();
    }

    public void removeLevel2() {
        if (mSubMenuLayout != null) {
            View v = mSubMenuLayout.getChildAt(0);
            mSubMenuLayout.removeView(v);
        }
    }

    public void showPopup(ListView popup, int level, boolean animate) {
        FrameLayout.LayoutParams layoutParams;
        hideUI();

        popup.setVisibility(View.VISIBLE);
        if (level == 1) {
            if (mMenuLayout == null) {
                mMenuLayout = new RotateLayout(mActivity, null);
                if (mRootView.getLayoutDirection() != View.LAYOUT_DIRECTION_RTL) {
                    layoutParams = new FrameLayout.LayoutParams(
                            CameraActivity.SETTING_LIST_WIDTH_1, LayoutParams.WRAP_CONTENT,
                            Gravity.LEFT | Gravity.TOP);
                } else {
                    layoutParams = new FrameLayout.LayoutParams(
                            CameraActivity.SETTING_LIST_WIDTH_1, LayoutParams.WRAP_CONTENT,
                            Gravity.RIGHT | Gravity.TOP);
                }
                mMenuLayout.setLayoutParams(layoutParams);
                ((ViewGroup) mRootView).addView(mMenuLayout);
            }
            mMenuLayout.setOrientation(mOrientation, true);
            mMenuLayout.addView(popup);
        }
        if (level == 2) {
            if (mSubMenuLayout == null) {
                mSubMenuLayout = new RotateLayout(mActivity, null);
                ViewGroup.LayoutParams params = new ViewGroup.LayoutParams(
                        CameraActivity.SETTING_LIST_WIDTH_2, LayoutParams.WRAP_CONTENT);
                mSubMenuLayout.setLayoutParams(params);

                ((ViewGroup) mRootView).addView(mSubMenuLayout);
            }
            if (mRootView.getLayoutDirection() != View.LAYOUT_DIRECTION_RTL) {
                layoutParams = new FrameLayout.LayoutParams(
                        CameraActivity.SETTING_LIST_WIDTH_2, LayoutParams.WRAP_CONTENT,
                        Gravity.LEFT | Gravity.TOP);
            } else {
                layoutParams = new FrameLayout.LayoutParams(
                        CameraActivity.SETTING_LIST_WIDTH_2, LayoutParams.WRAP_CONTENT,
                        Gravity.RIGHT | Gravity.TOP);
            }

            int screenHeight = (mOrientation == 0 || mOrientation == 180)
                    ? mRootView.getHeight() : mRootView.getWidth();
            int height = ((ListSubMenu) popup).getPreCalculatedHeight();
            int yBase = ((ListSubMenu) popup).getYBase();
            int y = Math.max(0, yBase);
            if (yBase + height > screenHeight)
                y = Math.max(0, screenHeight - height);
            if (mRootView.getLayoutDirection() != View.LAYOUT_DIRECTION_RTL) {
                layoutParams.setMargins(CameraActivity.SETTING_LIST_WIDTH_1, y, 0, 0);
            } else {
                layoutParams.setMargins(0, y, CameraActivity.SETTING_LIST_WIDTH_1, 0);
            }

            mSubMenuLayout.setLayoutParams(layoutParams);
            mSubMenuLayout.addView(popup);
            mSubMenuLayout.setOrientation(mOrientation, true);
        }
        if (animate) {
            if (level == 1)
                mVideoMenu.animateSlideIn(mMenuLayout, CameraActivity.SETTING_LIST_WIDTH_1, true);
            if (level == 2)
                mVideoMenu.animateFadeIn(popup);
        } else {
            popup.setAlpha(1f);
        }
    }

    public ViewGroup getMenuLayout() {
        return mMenuLayout;
    }

    public void setPreviewMenuLayout(LinearLayout layout) {
        mPreviewMenuLayout = layout;
    }

    public ViewGroup getPreviewMenuLayout() {
        return mPreviewMenuLayout;
    }

    public void showPopup(AbstractSettingPopup popup) {
        hideUI();

        if (mPopup != null) {
            mPopup.dismiss(false);
        }
        mPopup = new SettingsPopup(popup);
    }

    public void onShowSwitcherPopup() {
        hidePieRenderer();
    }

    public boolean hidePieRenderer() {
        if (mPieRenderer != null && mPieRenderer.showsItems()) {
            mPieRenderer.hide();
            return true;
        }
        return false;
    }

    // disable preview gestures after shutter is pressed
    public void setShutterPressed(boolean pressed) {
        if (mGestures == null) return;
        mGestures.setEnabled(!pressed);
    }

    public void enableShutter(boolean enable) {
        if (mShutterButton != null) {
            if (enable) {
                Log.v(TAG, "Shutter Button enabled !!");
            } else {
                Log.v(TAG, "Shutter Button disabled !!");
            }
            mShutterButton.setEnabled(enable);
        }
    }

    // PieListener
    @Override
    public void onPieOpened(int centerX, int centerY) {
        setSwipingEnabled(false);
        // Close module selection menu when pie menu is opened.
        mCameraControls.collapse();
    }

    @Override
    public void onPieClosed() {
        setSwipingEnabled(true);
    }

    public void setSwipingEnabled(boolean enable) {
        mActivity.setSwipingEnabled(enable);
    }

    public void showPreviewBorder(boolean enable) {
       // TODO: mPreviewFrameLayout.showBorder(enable);
    }

    // SingleTapListener
    // Preview area is touched. Take a picture.
    @Override
    public void onSingleTapUp(View view, int x, int y) {
        mController.onSingleTapUp(view, x, y);
    }

    public void showRecordingUI(boolean recording) {
        mRecordingStarted = recording;
        if (recording) {
            mShutterButton.setImageResource(R.drawable.shutter_button_video_stop);
            hideSwitcher();
        } else {
            mShutterButton.setImageResource(R.drawable.btn_new_shutter_video);
            if (!mController.isVideoCaptureIntent()) {
                showSwitcher();
            }
            stopRecordingTimer();
        }
    }

    public void hideUIwhileRecording() {
        mCameraControls.setMenuAndSwitcherEnabled(false);
    }

    public void showUIafterRecording() {
        mCameraControls.setMenuAndSwitcherEnabled(true);
    }

    public void showReviewImage(Bitmap bitmap) {
        mReviewImage.setImageBitmap(bitmap);
        mReviewImage.setVisibility(View.VISIBLE);
    }

    public void showReviewControls() {
        CameraUtil.fadeOut(mShutterButton);
        CameraUtil.fadeIn(mReviewDoneButton);
        CameraUtil.fadeIn(mReviewPlayButton);
        mReviewImage.setVisibility(View.VISIBLE);
        mCameraControls.hideCameraSettings();
    }

    public void hideReviewUI() {
        mReviewImage.setVisibility(View.GONE);
        mShutterButton.setEnabled(true);
        mCameraControls.hideCameraSettings();
        CameraUtil.fadeOut(mReviewDoneButton);
        CameraUtil.fadeOut(mReviewPlayButton);
        CameraUtil.fadeIn(mShutterButton);
    }

    public void onPreviewFocusChanged(boolean previewFocused) {
        super.onPreviewFocusChanged(previewFocused);

        if (mGestures != null) {
            mGestures.setEnabled(previewFocused);
        }
        if (mRenderOverlay != null) {
            // this can not happen in capture mode
            mRenderOverlay.setVisibility(previewFocused ? View.VISIBLE : View.GONE);
        }
    }

    public void initializeZoom(Parameters param) {
        if (param == null || !param.isZoomSupported() || mZoomRenderer == null) {
            mGestures.setZoomEnabled(false);
            return;
        }
        mZoomMax = param.getMaxZoom();
        mZoomRatios = param.getZoomRatios();
        // Currently we use immediate zoom for fast zooming to get better UX and
        // there is no plan to take advantage of the smooth zoom.
        mZoomRenderer.setZoomMax(mZoomMax);
        mZoomRenderer.setZoom(param.getZoom());
        mZoomRenderer.setZoomValue(mZoomRatios.get(param.getZoom()));
        mZoomRenderer.setOnZoomChangeListener(new ZoomChangeListener());
    }

    public void clickShutter() {
        mShutterButton.performClick();
    }

    public void pressShutter(boolean pressed) {
        mShutterButton.setPressed(pressed);
    }

    public View getShutterButton() {
        return mShutterButton;
    }

    public boolean isVisible() {
        return mCameraControls.getVisibility() == View.VISIBLE;
    }

    public boolean onScaleStepResize(boolean direction) {
        if(mGestures != null){
            return mGestures.onScaleStepResize(direction);
        }
        return false;
    }

    @Override
    public void onDisplayChanged() {
        mCameraControls.checkLayoutFlip();
        mController.updateCameraOrientation();
    }

    private class ZoomChangeListener implements ZoomRenderer.OnZoomChangedListener {
        @Override
        public void onZoomValueChanged(int index) {
            int newZoom = mController.onZoomChanged(index);
            if (mZoomRenderer != null) {
                mZoomRenderer.setZoomValue(mZoomRatios.get(newZoom));
            }
        }

        @Override
        public void onZoomStart() {
            if (mPieRenderer != null) {
                if (!mRecordingStarted) mPieRenderer.hide();
                mPieRenderer.setBlockFocus(true);
            }
        }

        @Override
        public void onZoomEnd() {
            if (mPieRenderer != null) {
                mPieRenderer.setBlockFocus(false);
            }
        }

        @Override
        public void onZoomValueChanged(float value) {

        }
    }

    // SurfaceHolder callbacks
    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        Log.v(TAG, "surfaceChanged: width = " + width + ", height = " + height);

        RectF r = new RectF(mSurfaceView.getLeft(), mSurfaceView.getTop(),
                mSurfaceView.getRight(), mSurfaceView.getBottom());
        mController.onPreviewRectChanged(CameraUtil.rectFToRect(r));

        onPreviewRectChanged(r);
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        Log.v(TAG, "surfaceCreated");
        mSurfaceHolder = holder;
        mController.onPreviewUIReady();
        mActivity.updateThumbnail(mThumbnail);
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        Log.v(TAG, "surfaceDestroyed");
        mSurfaceHolder = null;
        mController.onPreviewUIDestroyed();
    }

    public View getRootView() {
        return mRootView;
    }

    @Override
    public void onButtonPause() {
        mController.onButtonPause();
    }

    @Override
    public void onButtonContinue() {
        mController.onButtonContinue();
    }

    public void setPreference(String key, String value) {
        mVideoMenu.setPreference(key, value);
    }

    @Override
    public void setOrientation(int orientation, boolean animation) {
        super.setOrientation(orientation, animation);
        if (mMenuLayout != null) {
            mMenuLayout.setOrientation(orientation, animation);
        }
        if (mSubMenuLayout != null) {
            mSubMenuLayout.setOrientation(orientation, animation);
        }

        if (mPreviewMenuLayout != null) {
            ViewGroup vg = (ViewGroup) mPreviewMenuLayout.getChildAt(0);
            if (vg != null)
                vg = (ViewGroup) vg.getChildAt(0);
            if (vg != null) {
                for (int i = vg.getChildCount() - 1; i >= 0; --i) {
                    RotateLayout l = (RotateLayout) vg.getChildAt(i);
                    l.setOrientation(orientation, animation);
                }
            }
        }
        if (mZoomRenderer != null) {
            mZoomRenderer.setOrientation(orientation);
        }
        RotateTextToast.setOrientation(orientation);
        mOrientation = orientation;
    }

    public void tryToCloseSubList() {
        if (mVideoMenu != null)
            mVideoMenu.tryToCloseSubList();
    }

    public int getOrientation() {
        return mOrientation;
    }

    public void adjustOrientation() {
        setOrientation(mOrientation, false);
    }

    @Override
    public void onFaceDetection(Face[] faces, CameraProxy camera) {
        Log.d(TAG, "onFacedetectopmn");
        mFaceView.setFaces(faces);
    }

    public void pauseFaceDetection() {
        if (mFaceView != null) mFaceView.pause();
    }

    public void resumeFaceDetection() {
        if (mFaceView != null) mFaceView.resume();
    }

    public void onStartFaceDetection(int orientation, boolean mirror) {
        mFaceView.setBlockDraw(false);
        mFaceView.clear();
        mFaceView.setVisibility(View.VISIBLE);
        mFaceView.setDisplayOrientation(orientation);
        mFaceView.setMirror(mirror);
        mFaceView.resume();
    }

    public void onStopFaceDetection() {
        if (mFaceView != null) {
            mFaceView.setBlockDraw(true);
            mFaceView.clear();
        }
    }

    public FocusRing getFocusRing() {
        return mFocusRing;
    }
}
