/*
 * Copyright (c) 2019-2020 The Linux Foundation. All rights reserved.
 * Not a Contribution.
 *
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
package com.android.camera.multi;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.drawable.AnimationDrawable;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.view.SurfaceHolder;
import android.util.Log;

import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;

import com.android.camera.CameraActivity;
import com.android.camera.PauseButton;
import com.android.camera.PreviewGestures;
import com.android.camera.data.Camera2ModeAdapter;
import com.android.camera.ShutterButton;
import com.android.camera.ui.AutoFitSurfaceView;
import com.android.camera.ui.CameraControls;
import com.android.camera.ui.Camera2FaceView;
import com.android.camera.ui.CountDownView;
import com.android.camera.ui.FlashToggleButton;
import com.android.camera.ui.OneUICameraControls;
import com.android.camera.ui.RenderOverlay;
import com.android.camera.ui.RotateImageView;
import com.android.camera.ui.RotateLayout;

import org.codeaurora.snapcam.R;

import java.util.ArrayList;

public class MultiCameraUI implements PreviewGestures.SingleTapListener,
        PauseButton.OnPauseButtonListener {

    private static final String TAG = "SnapCam_MultiCameraUI";

    private static final int MAX_NUM_CAM = 16;

    private static final int PREVIEW_WIDTH = 1024;
    private static final int PREVIEW_HIEGHT = 768;

    private CameraActivity mActivity;
    private View mRootView;
    private MultiCameraModule mModule;
    private PreviewGestures mGestures;

    /**
     * An {@link AutoFitTextureView} for camera preview.
     */
    private AutoFitSurfaceView mMainPreviewSurface;
    private AutoFitSurfaceView mFirstPreviewSurface;
    private AutoFitSurfaceView mSecondPreviewSurface;
    private AutoFitSurfaceView mPreviewSurface;
    private ArrayList<AutoFitSurfaceView> mSurfaceViewList = new ArrayList();

    private SurfaceHolder mMainSurfaceHolder;
    private SurfaceHolder mFirstSurfaceHolder;
    private SurfaceHolder mSecondSurfaceHolder;

    private RenderOverlay mRenderOverlay;
    private ShutterButton mShutterButton;
    private PauseButton mPauseButton;
    private RotateImageView mMuteButton;
    private ImageView mVideoButton;
    private ImageView mThumbnail;
    private ImageView mSettingsIcon;

    private CountDownView mCountDownView;
    private OneUICameraControls mCameraControls;
    private Camera2FaceView mFaceView;

    private TextView mRecordingTimeView;
    private View mTimeLapseLabel;
    private RotateLayout mRecordingTimeRect;

    private FlashToggleButton mFlashButton;
    private View mFilterModeSwitcher;
    private View mSceneModeSwitcher;

    private int mOrientation;

    private RecyclerView mModeSelectLayout;
    private Camera2ModeAdapter mCameraModeAdapter;

    private int mPreviewWidths[] = new int[MAX_NUM_CAM];
    private int mPreviewHeights[] = new int[MAX_NUM_CAM];

    public MultiCameraUI(CameraActivity activity, final MultiCameraModule module, View parent) {
        mActivity = activity;
        mModule = module;
        mRootView = parent;
        mActivity.getLayoutInflater().inflate(R.layout.multi_camera_module,
                (ViewGroup) mRootView, true);

        initPreviewSurface();
        initCameraControls();
        initShutterButton();
        initVideoButton();
        initVideoMuteButton();
        initializeThumbnail();
        hideMenuButton();
        initPauseButton();
        initSettingsMenu();
        initModeSelectLayout();
    }

    @Override
    public void onButtonPause() {
        mRecordingTimeView.setCompoundDrawablesWithIntrinsicBounds(
                R.drawable.ic_pausing_indicator, 0, 0, 0);
        mModule.onButtonPause();
    }

    @Override
    public void onButtonContinue() {
        mRecordingTimeView.setCompoundDrawablesWithIntrinsicBounds(
                R.drawable.ic_recording_indicator, 0, 0, 0);
        mModule.onButtonContinue();
    }

    public void showSurfaceView(int index) {
        Log.d(TAG, "showSurfaceView" + mPreviewWidths[index] + " " + mPreviewHeights[index]);
        mSurfaceViewList.get(index).getHolder().setFixedSize(mPreviewWidths[index], mPreviewHeights[index]);
        mSurfaceViewList.get(index).setAspectRatio(mPreviewHeights[index], mPreviewWidths[index]);
        mSurfaceViewList.get(index).setVisibility(View.VISIBLE);
    }

    public boolean setPreviewSize(int index, int width, int height) {
        Log.d(TAG, "setPreviewSize " + width + " " + height);
        boolean changed = (width != mPreviewWidths[index]) || (height != mPreviewHeights[index]);
        mPreviewWidths[index] = width;
        mPreviewHeights[index] = height;
        if (changed) {
            showSurfaceView(index);
        }
        return changed;
    }

    private void initPreviewSurface() {
        // Multi camera preview
        mMainPreviewSurface = (AutoFitSurfaceView) mRootView.findViewById(R.id.main_preview_content);
        mFirstPreviewSurface = (AutoFitSurfaceView) mRootView.findViewById(R.id.first_preview_content);
        mSecondPreviewSurface = (AutoFitSurfaceView) mRootView.findViewById(R.id.second_preview_content);

        mSurfaceViewList.add(mMainPreviewSurface);
        mSurfaceViewList.add(mFirstPreviewSurface);
        mSurfaceViewList.add(mSecondPreviewSurface);

        mMainSurfaceHolder = mMainPreviewSurface.getHolder();
        mMainSurfaceHolder.addCallback(mMainSurfaceHolderCallback);
        mFirstSurfaceHolder = mFirstPreviewSurface.getHolder();
        mFirstSurfaceHolder.addCallback(mFirstHolderCallback);
        mSecondSurfaceHolder = mSecondPreviewSurface.getHolder();
        mSecondSurfaceHolder.addCallback(mSecondHolderCallback);

        mMainPreviewSurface.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
            @Override
            public void onLayoutChange(View v, int left, int top, int right,
                                       int bottom, int oldLeft, int oldTop, int oldRight,
                                       int oldBottom) {
                int width = right - left;
                int height = bottom - top;
            }
        });
    }

    private void initShutterButton() {
        mShutterButton = (ShutterButton) mRootView.findViewById(R.id.shutter_button);
        mShutterButton.setOnShutterButtonListener(mModule);
        mShutterButton.setImageResource(R.drawable.one_ui_shutter_anim);
        mShutterButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                doShutterAnimation();
            }
        });
    }

    private void initializeThumbnail() {
        if (mThumbnail == null) {
            mThumbnail = (ImageView) mRootView.findViewById(R.id.preview_thumb);
        }
        mActivity.updateThumbnail(mThumbnail);
        mThumbnail.setVisibility(View.INVISIBLE);
        mThumbnail.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!CameraControls.isAnimating() && !mModule.isTakingPicture() &&
                        !mModule.isRecordingVideo()) {
                    mActivity.gotoGallery();
                }
            }
        });
    }

    private void initModeSelectLayout() {
        mRenderOverlay = (RenderOverlay) mRootView.findViewById(R.id.render_overlay);
        mModeSelectLayout = (RecyclerView) mRootView.findViewById(R.id.mode_select_layout);
        mModeSelectLayout.setLayoutManager(new LinearLayoutManager(mActivity,
                LinearLayoutManager.HORIZONTAL, false));
        mCameraModeAdapter = new Camera2ModeAdapter(mModule.getCameraModeList());
        mCameraModeAdapter.setSelectedPosition(1);
        mCameraModeAdapter.setOnItemClickListener(mModule.getModeItemClickListener());
        mModeSelectLayout.setAdapter(mCameraModeAdapter);
        mModeSelectLayout.setVisibility(View.VISIBLE);

        if (mGestures == null) {
            // this will handle gesture disambiguation and dispatching
            mGestures = new PreviewGestures(mActivity, this, null, null, null);
            mRenderOverlay.setGestures(mGestures);
        }

        mGestures.setRenderOverlay(mRenderOverlay);
        mRenderOverlay.requestLayout();
        mActivity.setPreviewGestures(mGestures);
    }

    private void initCameraControls() {
        mCameraControls = (OneUICameraControls) mRootView.findViewById(R.id.camera_controls);
        mFaceView = (Camera2FaceView) mRootView.findViewById(R.id.face_view);
        mFaceView.initMode();
    }

    private void initSettingsMenu() {
        mSettingsIcon = (ImageView) mRootView.findViewById(R.id.settings);
        mSettingsIcon.setImageResource(R.drawable.settings);
        mSettingsIcon.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openSettingsMenu();
            }
        });
    }

    private void initPauseButton() {
        mRecordingTimeView = (TextView) mRootView.findViewById(R.id.recording_time);
        mRecordingTimeRect = (RotateLayout) mRootView.findViewById(R.id.multi_recording_time_rect);
        mTimeLapseLabel = mRootView.findViewById(R.id.time_lapse_label);

        mPauseButton = (PauseButton) mRootView.findViewById(R.id.video_pause);
        mPauseButton.setOnPauseButtonListener(this);
    }

    private void hideMenuButton() {
        if (mFlashButton == null) {
            mFlashButton = (FlashToggleButton) mRootView.findViewById(R.id.flash_button);
        }
        if (mFilterModeSwitcher == null) {
            mFilterModeSwitcher = mRootView.findViewById(R.id.filter_mode_switcher);
        }
        if (mSceneModeSwitcher == null) {
            mSceneModeSwitcher = mRootView.findViewById(R.id.scene_mode_switcher);
        }
        mFlashButton.setVisibility(View.INVISIBLE);
        mFilterModeSwitcher.setVisibility(View.INVISIBLE);
        mSceneModeSwitcher.setVisibility(View.INVISIBLE);
    }

    private void doShutterAnimation() {
        AnimationDrawable frameAnimation = (AnimationDrawable) mShutterButton.getDrawable();
        frameAnimation.stop();
        frameAnimation.start();
    }

    private void initVideoButton() {
        mVideoButton = (ImageView) mRootView.findViewById(R.id.video_button);
        mVideoButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                cancelCountDown();
                mModule.onVideoButtonClick();
            }
        });
    }

    private void initVideoMuteButton() {
        mMuteButton = (RotateImageView)mRootView.findViewById(R.id.mute_button);
        mMuteButton.setVisibility(View.VISIBLE);
        setMuteButtonResource(!mModule.isAudioMute());
        mMuteButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                boolean isEnabled = !mModule.isAudioMute();
                mModule.setMute(isEnabled, true);
                setMuteButtonResource(!isEnabled);
            }
        });
    }

    private void initializeCountDown() {
        mActivity.getLayoutInflater().inflate(R.layout.count_down_to_capture,
                (ViewGroup) mRootView, true);
        mCountDownView = (CountDownView) (mRootView.findViewById(R.id.count_down_to_capture));
        mCountDownView.setCountDownFinishedListener((CountDownView.OnCountDownFinishedListener) mModule);
        mCountDownView.bringToFront();
        mCountDownView.setOrientation(mOrientation);
    }

    private void setMuteButtonResource(boolean isUnMute) {
        if(isUnMute) {
            mMuteButton.setImageResource(R.drawable.ic_unmuted_button);
        } else {
            mMuteButton.setImageResource(R.drawable.ic_muted_button);
        }
    }

    public void setRecordingTime(String text) {
        mRecordingTimeView.setText(text);
    }

    public void setRecordingTimeTextColor(int color) {
        mRecordingTimeView.setTextColor(color);
    }

    public void resetPauseButton() {
        mRecordingTimeView.setCompoundDrawablesWithIntrinsicBounds(
                R.drawable.ic_recording_indicator, 0, 0, 0);
        mPauseButton.setPaused(false);
    }

    public void showRecordingUI(boolean recording) {
        if (recording) {
            mCameraControls.setVideoMode(true);
            mVideoButton.setImageResource(R.drawable.video_stop);
            mPauseButton.setVisibility(View.VISIBLE);
            mRecordingTimeView.setText("00:00");
            mRecordingTimeRect.setVisibility(View.VISIBLE);
            mMuteButton.setVisibility(View.INVISIBLE);
            setMuteButtonResource(!mModule.isAudioMute());
            showTimeLapseUI(false);
            mShutterButton.setVisibility(View.VISIBLE);
            mSettingsIcon.setVisibility(View.INVISIBLE);
        } else {
            //mFlashButton.setVisibility(View.VISIBLE);
            //mFlashButton.init(true);
            mCameraControls.setVideoMode(false);
            mPauseButton.setVisibility(View.INVISIBLE);
            mVideoButton.setImageResource(R.drawable.video_capture);
            mRecordingTimeRect.setVisibility(View.GONE);
            mMuteButton.setVisibility(View.INVISIBLE);
            mShutterButton.setVisibility(View.INVISIBLE);
            mSettingsIcon.setVisibility(View.VISIBLE);
        }
    }

    public void showTimeLapseUI(boolean enable) {
        if (mTimeLapseLabel != null) {
            mTimeLapseLabel.setVisibility(enable ? View.VISIBLE : View.GONE);
        }
    }

    /**
     * Enables or disables the shutter button.
     */
    public void enableShutter(boolean enabled) {
        if (mShutterButton != null) {
            mShutterButton.setEnabled(enabled);
        }
    }

    public void showRelatedIcons(MultiCameraModule.CameraMode mode) {
        //common settings
        mShutterButton.setVisibility(View.VISIBLE);
        //settings for each mode
        switch (mode) {
            case DEFAULT:
                mCameraControls.setVideoMode(false);
                mVideoButton.setVisibility(View.INVISIBLE);
                mMuteButton.setVisibility(View.INVISIBLE);
                mPauseButton.setVisibility(View.INVISIBLE);
                break;
            case VIDEO:
                mVideoButton.setVisibility(View.VISIBLE);
                mShutterButton.setVisibility(View.INVISIBLE);
                break;
            default:
                break;
        }
    }

    public void setOrientation(int orientation, boolean animation) {
        mOrientation = orientation;
        mCameraControls.setOrientation(orientation, animation);
        if (mRecordingTimeRect != null) {
            mRecordingTimeView.setRotation(-orientation);
        }
        if (mCountDownView != null)
            mCountDownView.setOrientation(orientation);

    }

    public int getOrientation() {
        return mOrientation;
    }

    public void cancelCountDown() {
        if (mCountDownView == null) return;
        mCountDownView.cancelCountDown();
    }

    public void initCountDownView() {
        if (mCountDownView == null) {
            initializeCountDown();
        } else {
            mCountDownView.initSoundPool();
        }
    }

    public void onCameraOpened(int cameraId) {
        mGestures.setMultiCameraUI(this);
    }

    public void swipeCameraMode(int move) {
        if (!mModule.getCameraModeSwitcherAllowed()) {
            return;
        }
        int index = mModule.getCurrentModeIndex() + move;
        int modeListSize = mModule.getCameraModeList().size();
        if (index >= modeListSize || index == -1) {
            return;
        }
        int mode = index % modeListSize;
        mModule.setCameraModeSwitcherAllowed(false);
        mCameraModeAdapter.setSelectedPosition(mode);
        mModeSelectLayout.smoothScrollToPosition(mode);
        mModule.selectCameraMode(mode);
    }

    public boolean isShutterEnabled() {
        return mShutterButton.isEnabled();
    }

    public ArrayList<AutoFitSurfaceView> getSurfaceViewList () {
        return mSurfaceViewList;
    }


    private void openSettingsMenu() {
        Intent intent = new Intent(mActivity, MultiSettingsActivity.class);
        intent.putExtra(MultiSettingsActivity.CAMERA_MODULE, mModule.getCurrenCameraMode());
        intent.putExtra(MultiSettingsActivity.CAMERA_ID_LISTS, mModule.getOpenCameraIdList());
        mActivity.startActivity(intent);
    }

    private void previewUIReady() {
        if((mMainSurfaceHolder != null && mMainSurfaceHolder.getSurface().isValid())) {
            mModule.onPreviewUIReady();
            if (mModule.isRecordingVideo() && mThumbnail != null){
                mThumbnail.setVisibility(View.INVISIBLE);
                mThumbnail = null;
                mActivity.updateThumbnail(mThumbnail);
            } else if (!mModule.isRecordingVideo()){
                if (mThumbnail == null)
                    mThumbnail = (ImageView) mRootView.findViewById(R.id.preview_thumb);
                mActivity.updateThumbnail(mThumbnail);
            }
        }
    }

    private SurfaceHolder.Callback mMainSurfaceHolderCallback = new SurfaceHolder.Callback() {

        @Override
        public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
            Log.v(TAG, "surfaceChanged: width =" + width + ", height = " + height);
        }

        @Override
        public void surfaceCreated(SurfaceHolder holder) {
            Log.v(TAG, "mMainSurfaceHolderCallback surfaceCreated");
            mMainSurfaceHolder = holder;
            previewUIReady();
        }

        @Override
        public void surfaceDestroyed(SurfaceHolder holder) {
            Log.v(TAG, "mMainSurfaceHolderCallback surfaceDestroyed");
            mMainSurfaceHolder = null;
        }
    };

    private SurfaceHolder.Callback mFirstHolderCallback = new SurfaceHolder.Callback() {

        @Override
        public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
            Log.v(TAG, "mFirstHolderCallback surfaceChanged: w : h =" + width + "x" + height);
        }

        @Override
        public void surfaceCreated(SurfaceHolder holder) {
            Log.v(TAG, "mFirstHolderCallback surfaceCreated");
            mFirstSurfaceHolder = holder;
        }

        @Override
        public void surfaceDestroyed(SurfaceHolder holder) {
            Log.v(TAG, "mFirstHolderCallback surfaceDestroyed");
            mFirstSurfaceHolder = null;
        }
    };

    private SurfaceHolder.Callback mSecondHolderCallback = new SurfaceHolder.Callback() {

        @Override
        public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
            Log.v(TAG, "mSecondHolderCallback surfaceChanged: w : h =" + width + "x" + height);
        }

        @Override
        public void surfaceCreated(SurfaceHolder holder) {
            Log.v(TAG, "mSecondHolderCallback surfaceCreated");
            mSecondSurfaceHolder = holder;
        }

        @Override
        public void surfaceDestroyed(SurfaceHolder holder) {
            Log.v(TAG, "mSecondHolderCallback surfaceDestroyed");
            mSecondSurfaceHolder = null;
        }
    };

    @Override
    public void onSingleTapUp(View view, int x, int y) {
        mModule.onSingleTapUp(view, x, y);
    }

    @Override
    public void onLongPress(View view, int x, int y) {
        mModule.onLongPress(view, x, y);
    }
}
