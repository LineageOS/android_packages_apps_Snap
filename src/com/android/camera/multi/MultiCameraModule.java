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

import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Rect;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCharacteristics;
import android.media.AudioManager;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.view.View;
import android.view.KeyEvent;
import android.view.OrientationEventListener;
import android.util.Log;

import java.util.List;
import java.util.ArrayList;
import java.util.Set;

import com.android.camera.CameraModule;
import com.android.camera.CameraActivity;
import com.android.camera.MediaSaveService;
import com.android.camera.PhotoController;
import com.android.camera.util.CameraUtil;
import com.android.camera.data.Camera2ModeAdapter.OnItemClickListener;

public class MultiCameraModule implements CameraModule, PhotoController {

    private static final String TAG = "SnapCam_MultiCameraModule";
    private static final int MAX_NUM_CAM = 16;

    private static final int OPEN_CAMERA = 0;

    private View mRootView;
    private MultiCameraUI mUI;
    private CameraActivity mActivity;
    private MultiCamera mMultiCamera;

    /**
     * An additional thread for running tasks that shouldn't block the UI.
     */
    private HandlerThread mCameraThread;
    private HandlerThread mCaptureCallbackThread;

    private Handler mCameraHandler;
    private Handler mCaptureCallbackHandler;

    private final Handler mHandler = new MainHandler();

    private int mDisplayRotation;
    private int mDisplayOrientation;

    private boolean mCameraModeSwitcherAllowed = true;
    private int mNextModeIndex = 1;
    private int mCurrentModeIndex = 1;

    // The degrees of the device rotated clockwise from its natural orientation.
    private int mOrientation = OrientationEventListener.ORIENTATION_UNKNOWN;

    private boolean mIsMute = false;

    private boolean[] mTakingPicture = new boolean[MAX_NUM_CAM];

    private ArrayList<SceneModule> mSceneCameraIds = new ArrayList<>();
    private String[] mSelectableModes = {"Video", "Photo"};
    private SceneModule mCurrentSceneMode;

    public static int CURRENT_ID = 0;
    public static CameraMode CURRENT_MODE = CameraMode.DEFAULT;

    public enum CameraMode {
        VIDEO,
        DEFAULT
    }

    public Handler getMainHandler() {
        return mHandler;
    }

    public Handler getMyCameraHandler() {
        return mCameraHandler;
    }

    public void reinit() {
    }

    public List<String> getCameraModeList() {
        ArrayList<String> cameraModes = new ArrayList<>();
        for (SceneModule sceneModule : mSceneCameraIds) {
            cameraModes.add(mSelectableModes[sceneModule.mode.ordinal()]);
        }
        return cameraModes;
    }

    public OnItemClickListener getModeItemClickListener() {
        return new OnItemClickListener() {
            @Override
            public int onItemClick(int mode) {
                Log.v(TAG, " onItemClick mode :" + mode);
                if (!getCameraModeSwitcherAllowed()) {
                    return -1;
                }
                return selectCameraMode(mode);
            }
        };
    }

    public int selectCameraMode(int mode) {
        if (mCurrentSceneMode.mode == mSceneCameraIds.get(mode).mode) {
            return -1;
        }
        setCameraModeSwitcherAllowed(true);
        setNextSceneMode(mode);
        mCurrentSceneMode = mSceneCameraIds.get(mode);
        SceneModule nextSceneMode = mSceneCameraIds.get(mode);
        restartAll();
        return 1;
    }

    public void setNextSceneMode(int index) {
        mNextModeIndex = index;
    }

    public void setCameraModeSwitcherAllowed(boolean allow) {
        mCameraModeSwitcherAllowed = allow;
    }

    public int getCurrentModeIndex() {
        return mCurrentModeIndex;
    }

    public void setCurrentSceneModeOnly(int mode) {
        mCurrentSceneMode = mSceneCameraIds.get(mode);
        mCurrentModeIndex = mode;
        CURRENT_ID = mCurrentSceneMode.getCurrentId();
        CURRENT_MODE = mCurrentSceneMode.mode;
    }

    public void restartAll() {
        Log.d(TAG, "restart all");
        onPauseBeforeSuper();
        onPauseAfterSuper();
        setCurrentSceneModeOnly(mNextModeIndex);
        updateMultiCamera();
        onResumeBeforeSuper();
        onResumeAfterSuper();
    }

    private void updateMultiCamera() {
        mMultiCamera = null;
        if (mCurrentSceneMode.mode == CameraMode.VIDEO) {
            mMultiCamera = new MultiVideoModule(mActivity, mUI, this);
        } else if (mCurrentSceneMode.mode == CameraMode.DEFAULT) {
            mMultiCamera = new MultiCaptureModule(mActivity, mUI, this);
        }
    }

    public boolean getCameraModeSwitcherAllowed() {
        return mCameraModeSwitcherAllowed;
    }

    public void onVideoButtonClick() {
        Log.d(TAG, "onVideoButtonClick");
        String[] cameraIds = {"0", "2"};
        mMultiCamera.onVideoButtonClick(cameraIds);
    }

    public boolean isTakingPicture() {
        for (int i = 0; i < mTakingPicture.length; i++) {
            if (mTakingPicture[i]) return true;
        }
        return false;
    }

    public boolean isRecordingVideo() {
        return mMultiCamera.isRecordingVideo();
    }

    public void updateTakingPicture(boolean update) {
        for (int i = 0; i < mTakingPicture.length; i++) {
            mTakingPicture[i] = false;
        }
    }

    public void setMute(boolean enable, boolean isValue) {
        AudioManager am = (AudioManager) mActivity.getSystemService(Context.AUDIO_SERVICE);
        am.setMicrophoneMute(enable);
        if (isValue) {
            mIsMute = enable;
        }
    }

    public boolean isAudioMute() {
        return mIsMute;
    }

    public void onButtonPause() {
        Log.v(TAG, "onButtonPause");
        String[] cameraIds = {"0", "2"};
        mMultiCamera.onButtonPause(cameraIds);
    }

    public void onButtonContinue() {
        Log.v(TAG, "onButtonContinue");
        String[] cameraIds = {"0", "2"};
        mMultiCamera.onButtonContinue(cameraIds);
    }

    public int[] getOpenCameraIdList() {
        int[] result = new int[2];
        result[0] = 0;
        result[1] = 2;
        return result;
    }

    public CameraMode getCurrenCameraMode() {
        if (mCurrentSceneMode == null) {
            Log.w(TAG, "getCurrenCameraMode mCurrentSceneMode is NULL retrun CameraMode.DEFAULT");
            return CameraMode.DEFAULT;
        } else {
            return mCurrentSceneMode.mode;
        }
    }

    @Override
    public void init(CameraActivity activity, View parent) {
        SceneModule module;
        Log.v(TAG, " init ");
        for (int i = 0; i < mSelectableModes.length; i++) {
            module = new SceneModule();
            module.mode = CameraMode.values()[i];
            mSceneCameraIds.add(module);
        }

        for (int i = 0; i < MAX_NUM_CAM; i++) {
            mTakingPicture[i] = false;
        }

        mCurrentSceneMode = mSceneCameraIds.get(1);

        mActivity = activity;
        mRootView = parent;
        if (mUI == null) {
            mUI = new MultiCameraUI(activity, this, parent);
        }
        mMultiCamera = new MultiCaptureModule(mActivity, mUI, this);
        startBackgroundThread();
        initCameraIds();
    }

    @Override
    public void onPreviewFocusChanged(boolean previewFocused) {

    }

    @Override
    public void onResumeBeforeSuper() {
        for (int i = 0; i < MAX_NUM_CAM; i++) {
            mTakingPicture[i] = false;
        }
        startBackgroundThread();
    }

    @Override
    public void onResumeAfterSuper() {
        Log.d(TAG, " onResumeAfterSuper ");
        mMultiCamera.onResume();
        Message msg = Message.obtain();
        msg.what = OPEN_CAMERA;
        if (mCameraHandler != null) {
            mCameraHandler.sendMessage(msg);
        }
        mUI.showRelatedIcons(mCurrentSceneMode.mode);
    }

    @Override
    public void onPauseBeforeSuper() {
        Log.d(TAG, " onPauseBeforeSuper ");
        mMultiCamera.onPause();
    }

    @Override
    public void onPauseAfterSuper() {
        stopBackgroundThread();
    }

    @Override
    public void onConfigurationChanged(Configuration config) {

    }

    @Override
    public void onStop() {

    }

    @Override
    public void onDestroy() {

    }

    @Override
    public void installIntentFilter() {

    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {

    }

    @Override
    public boolean onBackPressed() {
        return false;
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        return false;
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        return false;
    }

    @Override
    public void onSingleTapUp(View view, int x, int y) {

    }

    @Override
    public void onPreviewTextureCopied() {

    }

    @Override
    public void onCaptureTextureCopied() {

    }

    @Override
    public void onUserInteraction() {

    }

    @Override
    public boolean updateStorageHintOnResume() {
        return false;
    }

    @Override
    public void onOrientationChanged(int orientation) {
        // We keep the last known orientation. So if the user first orient
        // the camera then point the camera to floor or sky, we still have
        // the correct orientation.
        if (orientation == OrientationEventListener.ORIENTATION_UNKNOWN) return;
        int oldOrientation = mOrientation;
        mOrientation = CameraUtil.roundOrientation(orientation, mOrientation);
        if (oldOrientation != mOrientation) {
            mUI.setOrientation(mOrientation, true);
            mMultiCamera.onOrientationChanged(mOrientation);
        }

    }

    @Override
    public void onShowSwitcherPopup() {

    }

    @Override
    public void onMediaSaveServiceConnected(MediaSaveService s) {

    }

    @Override
    public boolean arePreviewControlsVisible() {
        return false;
    }

    @Override
    public void resizeForPreviewAspectRatio() {

    }

    @Override
    public void onSwitchSavePath() {

    }

    @Override
    public void waitingLocationPermissionResult(boolean waiting) {

    }

    @Override
    public void enableRecordingLocation(boolean enable) {

    }

    @Override
    public void setPreferenceForTest(String key, String value) {

    }

    @Override
    public int onZoomChanged(int requestedZoom) {
        return 0;
    }

    @Override
    public void onZoomChanged(float requestedZoom) {
    }

    @Override
    public boolean isImageCaptureIntent() {
        return false;
    }

    @Override
    public boolean isCameraIdle() {
        return true;
    }

    @Override
    public void onCaptureDone() {

    }

    @Override
    public void onCaptureCancelled() {

    }

    @Override
    public void onCaptureRetake() {

    }

    @Override
    public void cancelAutoFocus() {

    }

    @Override
    public void stopPreview() {

    }

    @Override
    public int getCameraState() {
        return 0;
    }

    @Override
    public void onCountDownFinished() {
    }

    @Override
    public void onScreenSizeChanged(int width, int height) {

    }

    @Override
    public void onPreviewRectChanged(Rect previewRect) {

    }

    @Override
    public void updateCameraOrientation() {
        if (mDisplayRotation != CameraUtil.getDisplayRotation(mActivity)) {
            setDisplayOrientation();
        }
    }

    @Override
    public void onPreviewUIReady() {
    }

    @Override
    public void onPreviewUIDestroyed() {
    }

    @Override
    public void onShutterButtonClick() {
        Log.d(TAG, "onShutterButtonClick");
        String[] cameraIds = {"0", "2"};
        mMultiCamera.onShutterButtonClick(cameraIds);
        for (String CameraId : cameraIds) {
            int id = Integer.parseInt(CameraId);
            mTakingPicture[id] = true;
        }
    }

    @Override
    public void onShutterButtonLongClick() {

    }

    @Override
    public void onShutterButtonFocus(boolean pressed) {

    }

    private class SceneModule {
        CameraMode mode = CameraMode.DEFAULT;
        public int rearCameraId;
        public int frontCameraId;
        public int auxCameraId = 0;
        int getCurrentId() {
            return rearCameraId;
        }
    }

    /**
     * This Handler is used to post message back onto the main thread of the
     * application
     */
    private class MainHandler extends Handler {
        public MainHandler() {
            super(Looper.getMainLooper());
        }

        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
            }
        }
    }

    private void initCameraIds() {
        CameraManager manager = (CameraManager) mActivity.getSystemService(Context.CAMERA_SERVICE);
        boolean isFirstDefault = true;
        String[] cameraIdList = null;
        try {
            cameraIdList = manager.getCameraIdList();
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
        if (cameraIdList == null || cameraIdList.length == 0) {
            return;
        }
        for (int i = 0; i < cameraIdList.length; i++) {
            String cameraId = cameraIdList[i];
            CameraCharacteristics characteristics;
            try {
                characteristics = manager.getCameraCharacteristics(cameraId);
            } catch (CameraAccessException e) {
                e.printStackTrace();
                continue;
            }
            Set<String> physicalCameraIds = characteristics.getPhysicalCameraIds();
            for (String camId : physicalCameraIds) {
                Log.v(TAG, "initCameraIds physicalCameraIds :" + physicalCameraIds);
                Log.v(TAG, "initCameraIds >>>> camId :" + camId + " cameraId :" + cameraId + ", i :" + i);
            }
        }
    }

    /**
         * Starts a background thread and its {@link Handler}.
         */
    private void startBackgroundThread() {
        if (mCameraThread == null) {
            mCameraThread = new HandlerThread("CameraBackground");
            mCameraThread.start();
        }
        if (mCaptureCallbackThread == null) {
            mCaptureCallbackThread = new HandlerThread("CameraCaptureCallback");
            mCaptureCallbackThread.start();
        }

        if (mCameraHandler == null) {
            mCameraHandler = new MyCameraHandler(mCameraThread.getLooper());
        }
        if (mCaptureCallbackHandler == null) {
            mCaptureCallbackHandler = new Handler(mCaptureCallbackThread.getLooper());
        }
    }

    private void stopBackgroundThread() {
        mCameraThread.quitSafely();
        try {
            mCameraThread.join();
            mCameraThread = null;
            mCameraHandler = null;
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        mCaptureCallbackThread.quitSafely();
        try {
            mCaptureCallbackThread.join();
            mCaptureCallbackThread = null;
            mCaptureCallbackHandler = null;
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    private class MyCameraHandler extends Handler {

        public MyCameraHandler(Looper looper) {
            super(looper);
        }

        @Override
        public void handleMessage(Message msg) {
            int id = msg.arg1;
            switch (msg.what) {
                case OPEN_CAMERA:
                    if (mMultiCamera != null) {
                        String[] cameraIds = {"0", "2"};
                        mMultiCamera.openCamera(cameraIds);
                    }
                    break;
            }
        }
    }

    private void setDisplayOrientation() {
        mDisplayRotation = CameraUtil.getDisplayRotation(mActivity);
        mDisplayOrientation = CameraUtil.getDisplayOrientationForCamera2(
                mDisplayRotation, 0);
    }

}