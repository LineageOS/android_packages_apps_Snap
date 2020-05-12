/*
 * Copyright (c) 2019-2020, The Linux Foundation. All rights reserved.
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
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.ImageFormat;
import android.graphics.Point;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CaptureFailure;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.OutputConfiguration;
import android.hardware.camera2.params.SessionConfiguration;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.location.Location;
import android.media.AudioManager;
import android.media.CamcorderProfile;
import android.media.Image;
import android.media.ImageReader;
import android.media.MediaRecorder;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.os.SystemClock;
import android.os.ParcelFileDescriptor;
import android.provider.MediaStore;
import android.util.Log;
import android.util.Size;
import android.view.OrientationEventListener;
import android.util.SparseIntArray;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.WindowManager;
import android.widget.Toast;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.concurrent.Executor;
import java.util.List;
import java.util.Date;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import com.android.camera.CameraActivity;
import com.android.camera.CaptureModule;
import com.android.camera.CameraSettings;
import com.android.camera.ComboPreferences;
import com.android.camera.Exif;
import com.android.camera.exif.ExifInterface;
import com.android.camera.LocationManager;
import com.android.camera.MediaSaveService;
import com.android.camera.PhotoModule.NamedImages;
import com.android.camera.PhotoModule.NamedImages.NamedEntity;
import com.android.camera.SDCard;
import com.android.camera.SoundClips;
import com.android.camera.Storage;
import com.android.camera.Thumbnail;
import com.android.camera.util.CameraUtil;
import com.android.camera.util.SettingTranslation;
import com.android.camera.util.PersistUtil;
import com.android.camera.ui.RotateTextToast;

import org.codeaurora.snapcam.R;

public class MultiVideoModule implements MultiCamera, LocationManager.Listener,
        MediaRecorder.OnErrorListener, MediaRecorder.OnInfoListener {

    private static final String TAG = "SnapCam_MultiVideoModule";

    private static final boolean DEBUG =
            (PersistUtil.getCamera2Debug() == PersistUtil.CAMERA2_DEBUG_DUMP_LOG) ||
                    (PersistUtil.getCamera2Debug() == PersistUtil.CAMERA2_DEBUG_DUMP_ALL);

    private static final int SENSOR_ORIENTATION_DEFAULT_DEGREES = 90;
    private static final int SENSOR_ORIENTATION_INVERSE_DEGREES = 270;
    private static final SparseIntArray DEFAULT_ORIENTATIONS = new SparseIntArray();
    private static final SparseIntArray INVERSE_ORIENTATIONS = new SparseIntArray();

    private static final int WAIT_SURFACE = 0;
    private static final int OPEN_CAMERA = 1;

    private static final int SCREEN_DELAY = 2 * 60 * 1000;

    private static final int MAX_NUM_CAM = 16;

    private int mCameraListIndex = 0;

    private static final CaptureRequest.Key<Byte> override_resource_cost_validation =
            new CaptureRequest.Key<>(
                    "org.codeaurora.qcamera3.sessionParameters.overrideResourceCostValidation",
                    byte.class);

    private CameraActivity mActivity;
    private MultiCameraUI mMultiCameraUI;
    private MultiCameraModule mMultiCameraModule;
    private SharedPreferences mLocalSharedPref;
    private ArrayList<CameraCharacteristics> mCharacteristics;
    private CameraDevice[] mCameraDevices = new CameraDevice[MAX_NUM_CAM];
    private CameraCaptureSession[] mCameraPreviewSessions = new CameraCaptureSession[MAX_NUM_CAM];
    private ContentValues[] mCurrentVideoValues = new ContentValues[MAX_NUM_CAM];
    private ImageReader[] mImageReaders = new ImageReader[MAX_NUM_CAM];
    private MediaRecorder[] mMediaRecorders = new MediaRecorder[MAX_NUM_CAM];
    private String[] mNextVideoAbsolutePaths = new String[MAX_NUM_CAM];
    private boolean mPaused = true;

    private NamedImages mNamedImages;

    private Uri mCurrentVideoUri;

    private boolean mMediaRecorderPausing = false;

    private boolean mRecordingTimeCountsDown = false;

    private LocationManager mLocationManager;
    private CamcorderProfile mProfile;

    private Size[] mVideoSize = new Size[MAX_NUM_CAM];
    private Size mPreviewSizes[] = new Size[MAX_NUM_CAM];
    private int[][] mMaxPreviewSize = new int[MAX_NUM_CAM][];

    private boolean mCaptureTimeLapse = false;
    // Default 0. If it is larger than 0, the camcorder is in time lapse mode.
    private int mTimeBetweenTimeLapseFrameCaptureMs = 0;

    private String[] mVideoFilenames = new String[MAX_NUM_CAM];

    private long mRecordingStartTime;
    private long mRecordingTotalTime;

    private ParcelFileDescriptor mVideoFileDescriptor;

    // The video duration limit. 0 means no limit.
    private int mMaxVideoDurationInMs;

    private int mAudioEncoder;
    private String mVideoRotation;

    private SoundClips.Player mSoundPlayer;

    /**
     * Whether the app is recording video now
     */
    private boolean[] mIsRecordingVideos = new boolean[MAX_NUM_CAM];

    private String[] mCameraIds;
    private ArrayList<String> mCameraIDList = new ArrayList<>();

    /**
     * Orientation of the camera sensor
     */
    private int mSensorOrientation;
    private int mOrientation = OrientationEventListener.ORIENTATION_UNKNOWN;

    /**
     * {@link CaptureRequest.Builder} for the camera preview
     */
    private CaptureRequest.Builder[] mPreviewRequestBuilders = new CaptureRequest.Builder[MAX_NUM_CAM];

    private Handler mCameraHandler;
    private HandlerThread mCameraThread;

    private ContentResolver mContentResolver;
    /**
     * A {@link Semaphore} make sure the camera open callback happens first before closing the
     * camera.
     */
    private Semaphore mCameraOpenCloseLock = new Semaphore(3);

    public MultiVideoModule(CameraActivity activity, MultiCameraUI ui, MultiCameraModule module) {
        mActivity = activity;
        mMultiCameraUI = ui;
        mMultiCameraModule = module;
        mContentResolver = mActivity.getContentResolver();
        mLocationManager = new LocationManager(mActivity, this);
        mNamedImages = new NamedImages();
        mLocalSharedPref = mActivity.getSharedPreferences(
                ComboPreferences.getLocalSharedPreferencesName(mActivity,
                        "multi" + mMultiCameraModule.getCurrenCameraMode()), Context.MODE_PRIVATE);
        startBackgroundThread();
        initCameraCharacteristics();
    }

    @Override
    public void onResume(String[] ids) {
        for (String id : ids) {
            mCameraIDList.add(id);
        }
        // Set up sound playback for video record and video stop
        if (mSoundPlayer == null) {
            mSoundPlayer = SoundClips.getPlayer(mActivity);
        }
        mPaused = false;
        initializeValues();
        startBackgroundThread();
        for (String id : ids) {
            int cameraId = Integer.parseInt(id);
            updateVideoSize(cameraId);
            int index = mCameraIDList.indexOf(id);
            mMultiCameraUI.setPreviewSize(index, mPreviewSizes[cameraId].getWidth(),
                    mPreviewSizes[cameraId].getHeight());
        }
    }

    @Override
    public void onPause() {
        mPaused = true;
        if (mSoundPlayer != null) {
            mSoundPlayer.release();
            mSoundPlayer = null;
        }
        for (String id : mCameraIds) {
            int cameraId = Integer.parseInt(id);
            Log.d(TAG, " onPause id :" + cameraId + "  recording is :" +
                    (mIsRecordingVideos[cameraId] ? "STOPED" : "START"));
            if (mIsRecordingVideos[cameraId]) {
                stopRecordingVideo(cameraId);
            }
        }

        if (mCameraIDList != null) {
            mCameraIDList.clear();
        }
        mCameraListIndex = 0;
        closeCamera();
        stopBackgroundThread();
    }

    @Override
    public boolean openCamera(String[] ids) {
        mCameraIds = ids;
        for (String id : ids) {
            mCameraIDList.add(id);
        }
        Message msg = Message.obtain();
        msg.what = OPEN_CAMERA;
        if (mCameraHandler != null) {
            mCameraHandler.sendMessage(msg);
        }
        return true;
    }

    @Override
    public void startPreview() {

    }

    @Override
    public void closeSession() {

    }

    @Override
    public void closeCamera() {
        Log.d(TAG, "closeCamera");
        /* no need to set this in the callback and handle asynchronously. This is the same
        reason as why we release the semaphore here, not in camera close callback function
        as we don't have to protect the case where camera open() gets called during camera
        close(). The low level framework/HAL handles the synchronization for open()
        happens after close() */
        try {
            // Close camera starting with AUX first
            for (int i = MAX_NUM_CAM - 1; i >= 0; i--) {
                if (null != mCameraDevices[i]) {
                    if (!mCameraOpenCloseLock.tryAcquire(2000, TimeUnit.MILLISECONDS)) {
                        Log.d(TAG, "Time out waiting to lock camera closing.");
                        throw new RuntimeException("Time out waiting to lock camera closing");
                    }
                    Log.d(TAG, "Closing camera: " + mCameraDevices[i].getId());
                    mCameraDevices[i].close();
                    mCameraDevices[i] = null;
                    mCameraPreviewSessions[i] = null;
                }
                if (null != mImageReaders[i]) {
                    mImageReaders[i].close();
                    mImageReaders[i] = null;
                }
            }
        } catch (InterruptedException e) {
            mCameraOpenCloseLock.release();
            throw new RuntimeException("Interrupted while trying to lock camera closing.", e);
        } catch (IllegalStateException e) {
            e.printStackTrace();
        } finally {
            mCameraOpenCloseLock.release();
        }
    }

    @Override
    public void onVideoButtonClick(String[] ids) {
        checkAndPlayShutterSound(mIsRecordingVideos[0]);
        for (String id : ids) {
            int cameraId = Integer.parseInt(id);
            Log.d(TAG, " onVideoButtonClick id :" + cameraId + "  recording is :" +
                    (mIsRecordingVideos[cameraId] ? "STOPED" : "START"));
            if (mIsRecordingVideos[cameraId]) {
                stopRecordingVideo(cameraId);
            } else {
                startRecordingVideo(cameraId);
            }
        }
    }

    @Override
    public void onShutterButtonClick(String[] ids) {
        checkAndPlayCaptureSound();
        mMultiCameraUI.enableShutter(false);
        for (String id : ids) {
            Log.d(TAG, "onShutterButtonClick id :" + id);
            try {
                int cameraId = Integer.parseInt(id);
                if (null == mActivity || null == mCameraDevices[cameraId]) {
                    warningToast("Camera is not ready yet to take a video snapshot.");
                    return;
                }
                final CaptureRequest.Builder captureBuilder =
                        mCameraDevices[cameraId].createCaptureRequest(
                                CameraDevice.TEMPLATE_VIDEO_SNAPSHOT);
                captureBuilder.addTarget(mImageReaders[cameraId].getSurface());
                int index = mCameraIDList.indexOf(String.valueOf(cameraId));
                captureBuilder.addTarget(mMultiCameraUI.getSurfaceViewList().get(
                        index).getHolder().getSurface());
                // Use the same AE and AF modes as the preview.
                captureBuilder.set(CaptureRequest.CONTROL_AF_MODE,
                        CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
                captureBuilder.set(CaptureRequest.JPEG_THUMBNAIL_QUALITY, (byte) 80);
                captureBuilder.set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_AUTO);
                captureBuilder.set(CaptureRequest.CONTROL_AE_MODE,
                        CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH);

                // Orientation
                int rotation = mActivity.getWindowManager().getDefaultDisplay().getRotation();
                captureBuilder.set(CaptureRequest.JPEG_ORIENTATION,
                        CameraUtil.getJpegRotation(cameraId, rotation));
                mCameraPreviewSessions[cameraId].capture(captureBuilder.build(),
                        mCaptureStillCallback, mMultiCameraModule.getMyCameraHandler());
                Log.d(TAG, " cameraCaptureSession" + id + " captured ");
            } catch (CameraAccessException e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public void onButtonPause(String[] ids) {
        mRecordingTotalTime += SystemClock.uptimeMillis() - mRecordingStartTime;
        mMediaRecorderPausing = true;
        for (String id : ids) {
            int cameraId = Integer.parseInt(id);
            mMediaRecorders[cameraId].pause();
        }
    }

    @Override
    public void onButtonContinue(String[] ids) {
        mMediaRecorderPausing = false;
        for (String id : ids) {
            int cameraId = Integer.parseInt(id);
            mMediaRecorders[cameraId].resume();
            mRecordingStartTime = SystemClock.uptimeMillis();
            updateRecordingTime(cameraId);
        }
    }

    @Override
    public void onErrorListener(int error) {
        enableRecordingLocation(false);
    }

    // from MediaRecorder.OnErrorListener
    @Override
    public void onError(MediaRecorder mr, int what, int extra) {
        Log.e(TAG, "MediaRecorder error. what=" + what + ". extra=" + extra);
        String[] ids = {"0", "1"};
        for (String id : ids) {
            int cameraId = Integer.parseInt(id);
            stopRecordingVideo(cameraId);
        }
        if (what == MediaRecorder.MEDIA_RECORDER_ERROR_UNKNOWN) {
            // We may have run out of space on the sdcard.
            mActivity.updateStorageSpaceAndHint();
        } else {
            warningToast("MediaRecorder error. what=" + what + ". extra=" + extra);
        }
    }

    // from MediaRecorder.OnInfoListener
    @Override
    public void onInfo(MediaRecorder mr, int what, int extra) {
        String[] ids = {"0", "1"};
        if (what == MediaRecorder.MEDIA_RECORDER_INFO_MAX_DURATION_REACHED) {
            for (String id : ids) {
                int cameraId = Integer.parseInt(id);
                if (mIsRecordingVideos[cameraId]) {
                    stopRecordingVideo(cameraId);
                }
            }
        } else if (what == MediaRecorder.MEDIA_RECORDER_INFO_MAX_FILESIZE_REACHED) {
            for (String id : ids) {
                int cameraId = Integer.parseInt(id);
                if (mIsRecordingVideos[cameraId]) {
                    stopRecordingVideo(cameraId);
                }
            }
            // Show the toast.
            RotateTextToast.makeText(mActivity, R.string.video_reach_size_limit,
                    Toast.LENGTH_LONG).show();
        }
    }

    @Override
    public void onOrientationChanged(int orientation) {
        mOrientation = orientation;
    }

    @Override
    public boolean isRecordingVideo() {
        for (int i = 0; i < mIsRecordingVideos.length; i++) {
            if (mIsRecordingVideos[i]) return true;
        }
        return false;
    }

    private void openCameraInSequence(String id) {
        CameraManager manager = (CameraManager) mActivity.getSystemService(Context.CAMERA_SERVICE);
        CameraCharacteristics characteristics = null;
        try {
            characteristics = manager.getCameraCharacteristics(id);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
        mSensorOrientation = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION);
        Log.d(TAG, "openCameraInSequence " + id + ", mSensorOrientation :" + mSensorOrientation);
        try {
            if (!mCameraOpenCloseLock.tryAcquire(5000, TimeUnit.MILLISECONDS)) {
                Log.d(TAG, "Time out waiting to lock camera opening.");
                throw new RuntimeException("Time out waiting to lock camera opening");
            }
            manager.openCamera(id, mStateCallback, mMultiCameraModule.getMyCameraHandler());
        } catch (CameraAccessException e) {
            e.printStackTrace();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        manager = null;
        characteristics = null;
    }

    private void initCameraCharacteristics() {
        mCharacteristics = new ArrayList<>();
        CameraManager manager = (CameraManager) mActivity.getSystemService(Context.CAMERA_SERVICE);
        try {
            String[] cameraIdList = manager.getCameraIdList();
            Log.d(TAG, "cameraIdList size =" + cameraIdList.length);
            for (int i = 0; i < cameraIdList.length; i++) {
                String cameraId = cameraIdList[i];
                CameraCharacteristics characteristics
                        = manager.getCameraCharacteristics(cameraId);
                mCharacteristics.add(i, characteristics);
                try {
                    mMaxPreviewSize[i] = characteristics.get(CaptureModule.max_preview_size);
                } catch (IllegalArgumentException e) {
                    Log.e(TAG, "getMaxPreviewSize no vendorTag max_preview_size:");
                }
                if (mMaxPreviewSize[i] != null) {
                    Log.d(TAG, " init cameraId :" + cameraId + ", i :" + i +
                            ", maxPreviewSize :" + mMaxPreviewSize[i][0]+ "x" +
                            mMaxPreviewSize[i][1]);
                }
            }
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private void startBackgroundThread() {
        if (mCameraThread == null) {
            mCameraThread = new HandlerThread("CameraBackground");
            mCameraThread.start();
        }
        if (mCameraHandler == null) {
            mCameraHandler = new MyCameraHandler(mCameraThread.getLooper());
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
    }

    private class MyCameraHandler extends Handler {

        public MyCameraHandler(Looper looper) {
            super(looper);
        }

        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case WAIT_SURFACE:
                    int id = msg.arg1;
                    int index = mCameraIDList.indexOf(String.valueOf(id));
                    Log.v(TAG, "WAIT_SURFACE id :" + id + ", index :" + index);
                    if (index == -1) {
                        break;
                    }
                    Surface surface = mMultiCameraUI.getSurfaceViewList().get(index)
                            .getHolder().getSurface();
                    if (surface.isValid()) {
                        createCaptureSessions(id, surface);
                    } else {
                        mCameraHandler.sendMessageDelayed(msg, 200);
                        Log.v(TAG, "Surface is invalid, wait more 200ms surfaceCreated");
                    }
                    break;
                case OPEN_CAMERA:
                    if (mCameraListIndex == mCameraIDList.size()) {
                        mCameraListIndex = 0;
                    } else {
                        String cameraId = mCameraIDList.get(mCameraListIndex);
                        openCameraInSequence(cameraId);
                        mCameraListIndex ++;
                        Log.v(TAG, " OPEN_CAMERA cameraId :" + cameraId + ", mCameraListIndex :"
                                + mCameraListIndex);
                    }
                    break;
            }
        }
    }

    private void createVideoSnapshotImageReader(int id) {
        if (mImageReaders[id] != null) {
            mImageReaders[id].close();
        }
        mImageReaders[id] = ImageReader.newInstance(mVideoSize[id].getWidth(),
                mVideoSize[id].getHeight(),
                ImageFormat.JPEG, /*maxImages*/2);
        mImageReaders[id].setOnImageAvailableListener(
                mOnImageAvailableListener, mMultiCameraModule.getMyCameraHandler());
    }

    private final CameraDevice.StateCallback mStateCallback = new CameraDevice.StateCallback() {

        @Override
        public void onOpened(CameraDevice cameraDevice) {
            int id = Integer.parseInt(cameraDevice.getId());
            mCameraDevices[id] = cameraDevice;
            Log.d(TAG, "onOpened " + id);
            mCameraOpenCloseLock.release();
            createCameraPreviewSession(id);
            mActivity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    mMultiCameraUI.onCameraOpened(id);
                }
            });
        }

        @Override
        public void onDisconnected(CameraDevice cameraDevice) {
            int id = Integer.parseInt(cameraDevice.getId());
            Log.d(TAG, "onDisconnected " + id);
            mCameraOpenCloseLock.release();
            mCameraDevices[id] = null;
        }

        @Override
        public void onError(CameraDevice cameraDevice, int error) {
            int id = Integer.parseInt(cameraDevice.getId());
            Log.e(TAG, "onError " + id + " " + error);
            mCameraOpenCloseLock.release();

            if (null != mActivity) {
                Toast.makeText(mActivity,"open camera error id =" + id,
                        Toast.LENGTH_LONG).show();
                mActivity.finish();
            }
        }

        @Override
        public void onClosed(CameraDevice cameraDevice) {
            int id = Integer.parseInt(cameraDevice.getId());
            Log.d(TAG, "onClosed " + id);
            mCameraOpenCloseLock.release();
            mCameraDevices[id] = null;
        }
    };

    private CameraCaptureSession.CaptureCallback mCaptureStillCallback
            = new CameraCaptureSession.CaptureCallback() {

        @Override
        public void onCaptureCompleted(CameraCaptureSession session, CaptureRequest request,
                                       TotalCaptureResult result) {
            Log.d(TAG, " mCaptureCallback onCaptureCompleted ");
            mMultiCameraModule.getMainHandler().post(new Runnable() {
                @Override
                public void run() {
                    Log.d(TAG, " enable Shutter " );
                    mMultiCameraUI.enableShutter(true);
                }
            });
        }

        @Override
        public void onCaptureFailed(CameraCaptureSession session,
                                    CaptureRequest request,
                                    CaptureFailure result) {
            Log.d(TAG, " mCaptureCallback onCaptureFailed  " );
        }


        @Override
        public void onCaptureSequenceCompleted(CameraCaptureSession session, int
                sequenceId, long frameNumber) {
            Log.d(TAG, " mCaptureCallback onCaptureSequenceCompleted ");
        }
    };

    private MediaSaveService.OnMediaSavedListener mOnMediaSavedListener =
            new MediaSaveService.OnMediaSavedListener() {
                @Override
                public void onMediaSaved(Uri uri) {
                    if (uri != null) {
                        mActivity.notifyNewMedia(uri);
                    }
                }
            };

    public void enableRecordingLocation(boolean enable) {
        mLocationManager.recordLocation(enable);
    }

    private Size parsePictureSize(String value) {
        int indexX = value.indexOf('x');
        int width = Integer.parseInt(value.substring(0, indexX));
        int height = Integer.parseInt(value.substring(indexX + 1));
        return new Size(width, height);
    }

    private void updateVideoSize(int id) {
        String defaultSize = mActivity.getString(R.string.pref_multi_camera_video_quality_default);
        int index = mCameraIDList.indexOf(String.valueOf(id));
        String videoSize = mLocalSharedPref.getString(
                MultiSettingsActivity.KEY_VIDEO_SIZES.get(index), defaultSize);
        Size size = parsePictureSize(videoSize);
        mVideoSize[id] = size;
        Log.v(TAG, " updateVideoSize size :" + mVideoSize[id].getWidth() + "x" +
                mVideoSize[id].getHeight());

        Size[] prevSizes = getSupportedOutputSize(id, MediaRecorder.class);
        mPreviewSizes[id] = getOptimalVideoPreviewSize(id, mVideoSize[id], prevSizes);
    }

    private Size[] getSupportedOutputSize(int cameraId, Class cl) {
        StreamConfigurationMap map = mCharacteristics.get(cameraId).get(
                CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
        Size[] normal = map.getOutputSizes(cl);
        Size[] high = map.getHighResolutionOutputSizes(ImageFormat.PRIVATE);
        Size[] ret = new Size[normal.length + high.length];
        System.arraycopy(normal, 0, ret, 0, normal.length);
        System.arraycopy(high, 0, ret, normal.length, high.length);
        return ret;
    }

    private Size getOptimalVideoPreviewSize(int id, Size VideoSize, Size[] prevSizes) {
        Point[] points = new Point[prevSizes.length];

        int index = 0;
        int point_max[] = mMaxPreviewSize[id];
        int max_size = -1;
        if (point_max != null) {
            max_size = point_max[0] * point_max[1];
        }
        for (Size s : prevSizes) {
            if (max_size != -1) {
                int size = s.getWidth() * s.getHeight();
                if (s.getWidth() == s.getHeight()) {
                    if (s.getWidth() > Math.max(point_max[0], point_max[1]))
                        continue;
                } else if (size > max_size || size == 0) {
                    continue;
                }
            }
            points[index++] = new Point(s.getWidth(), s.getHeight());
        }

        int optimalPickIndex = CameraUtil.getOptimalVideoPreviewSize(mActivity, points, VideoSize);
        return (optimalPickIndex == -1) ? null :
                new Size(points[optimalPickIndex].x, points[optimalPickIndex].y);
    }

    /**
     * Creates a new {@link CameraCaptureSession} for camera preview.
     */
    private void createCameraPreviewSession(int id) {
        // This is the output Surface we need to start preview.
        int index = mCameraIDList.indexOf(String.valueOf(id));
        Log.v(TAG, "createCameraPreviewSession id :" + id + ", index :" + index);
        Surface surface = mMultiCameraUI.getSurfaceViewList().get(index).getHolder().getSurface();

        if (surface.isValid()) {
            createCaptureSessions(id, surface);
        } else {
            Message msg = Message.obtain();
            msg.what = WAIT_SURFACE;
            msg.arg1 = id;
            mCameraHandler.sendMessageDelayed(msg, 200);
            Log.v(TAG, "Surface is invalid, wait 200ms surfaceCreated");
        }

    }

    private void createCaptureSessions(int id, Surface surface) {
        try {
            // We set up a CaptureRequest.Builder with the output Surface.
            mPreviewRequestBuilders[id]
                    = mCameraDevices[id].createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            mPreviewRequestBuilders[id].addTarget(surface);
            mPreviewRequestBuilders[id].setTag(id);

            CameraCaptureSession.StateCallback stateCallback =
                    new CameraCaptureSession.StateCallback() {
                        @Override
                        public void onConfigured(CameraCaptureSession cameraCaptureSession) {
                            // The camera is already closed
                            if (null == mCameraDevices[id]) {
                                return;
                            }
                            Log.v(TAG, " CameraCaptureSession onConfigured id :" + id);
                            // When the session is ready, we start displaying the preview.
                            mCameraPreviewSessions[id] = cameraCaptureSession;
                            try {
                                // Auto focus should be continuous for camera preview.
                                mPreviewRequestBuilders[id].set(CaptureRequest.CONTROL_AF_MODE,
                                        CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
                                // Finally, we start displaying the camera preview.
                                mCameraPreviewSessions[id].setRepeatingRequest(
                                        mPreviewRequestBuilders[id].build(),
                                        mCaptureCallback, mMultiCameraModule.getMyCameraHandler());
                            } catch (CameraAccessException e) {
                                e.printStackTrace();
                            }

                            if (mCameraDevices[mCameraListIndex] == null) {
                                Message msg = Message.obtain();
                                msg.what = OPEN_CAMERA;
                                if (mCameraHandler != null) {
                                    mCameraHandler.sendMessage(msg);
                                }
                            }
                        }

                        @Override
                        public void onConfigureFailed(CameraCaptureSession cameraCaptureSession) {
                            showToast("onConfigureFailed");
                        }
                    };

            try {
                final byte enable = 1;
                mPreviewRequestBuilders[id].set(override_resource_cost_validation, enable);
                Log.v(TAG, " set" + override_resource_cost_validation + " is 1");
            } catch (IllegalArgumentException e) {
                e.printStackTrace();
            }

            List<OutputConfiguration> outConfigurations = new ArrayList<>(1);
            outConfigurations.add(new OutputConfiguration(surface));

            SessionConfiguration sessionConfiguration = new SessionConfiguration(
                    SessionConfiguration.SESSION_REGULAR, outConfigurations,
                    new HandlerExecutor(mCameraHandler), stateCallback);
            sessionConfiguration.setSessionParameters(mPreviewRequestBuilders[id].build());
            mCameraDevices[id].createCaptureSession(sessionConfiguration);

        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private class HandlerExecutor implements Executor {
        private final Handler ihandler;

        public HandlerExecutor(Handler handler) {
            ihandler = handler;
        }

        @Override
        public void execute(Runnable runCmd) {
            ihandler.post(runCmd);
        }
    }

    /**
     * A {@link CameraCaptureSession.CaptureCallback} that handles events related to JPEG capture.
     */
    private CameraCaptureSession.CaptureCallback mCaptureCallback
            = new CameraCaptureSession.CaptureCallback() {

        private void process(CaptureResult result) {
            Integer afState = result.get(CaptureResult.CONTROL_AF_STATE);
            Integer aeState = result.get(CaptureResult.CONTROL_AE_STATE);
            if (DEBUG) {
                Log.v(TAG, "process afState :" + afState + ", aeState :" + aeState);
            }
        }

        @Override
        public void onCaptureProgressed(CameraCaptureSession session, CaptureRequest request,
                                        CaptureResult partialResult) {
            process(partialResult);
        }

        @Override
        public void onCaptureCompleted(CameraCaptureSession session, CaptureRequest request,
                                       TotalCaptureResult result) {
            process(result);
        }

    };

    private final ImageReader.OnImageAvailableListener mOnImageAvailableListener
            = new ImageReader.OnImageAvailableListener() {

        @Override
        public void onImageAvailable(ImageReader reader) {
            Log.v(TAG, " onImageAvailable ...");
            Image image = reader.acquireNextImage();
            long imageTime = System.currentTimeMillis();
            mNamedImages.nameNewImage(imageTime);
            NamedEntity name = mNamedImages.getNextNameEntity();
            String title = (name == null) ? null : name.title;
            long date = (name == null) ? -1 : name.date;

            ByteBuffer buffer = image.getPlanes()[0].getBuffer();
            byte[] bytes = new byte[buffer.remaining()];
            buffer.get(bytes);

            int orientation = 0;
            ExifInterface exif = Exif.getExif(bytes);
            orientation = Exif.getOrientation(exif);
            String saveFormat = "jpeg";
            mActivity.getMediaSaveService().addImage(bytes, title, date,
                    null, image.getWidth(), image.getHeight(), orientation, exif,
                    mOnMediaSavedListener, mContentResolver, saveFormat);
            mActivity.updateThumbnail(bytes);
            image.close();
            mMultiCameraModule.updateTakingPicture();
        }
    };

    private void initializeValues() {
        updateMaxVideoDuration();
        updateAudioEncoder();
        updateVideoRotation();
    }

    private void checkAndPlayShutterSound(boolean isStarted) {
        if (mSoundPlayer != null) {
            mSoundPlayer.play(isStarted? SoundClips.STOP_VIDEO_RECORDING
                    : SoundClips.START_VIDEO_RECORDING);
        }
    }

    private void checkAndPlayCaptureSound() {
        if (mSoundPlayer != null) {
            mSoundPlayer.play(SoundClips.SHUTTER_CLICK);
        }
    }

    private void closePreviewSession(int id) {
        if (mCameraPreviewSessions[id] != null) {
            Log.v(TAG, "closePreviewSession id :" + id);
            mCameraPreviewSessions[id].close();
            mCameraPreviewSessions[id] = null;
        }
    }

    private void startRecordingVideo(final int id) {
        int index = mCameraIDList.indexOf(String.valueOf(id));
        if (null == mCameraDevices[id] ||
                !mMultiCameraUI.getSurfaceViewList().get(index).isEnabled()) {
            return;
        }
        Log.v(TAG, " startRecordingVideo " + id);
        try {
            closePreviewSession(id);
            setUpMediaRecorder(id);
            createVideoSnapshotImageReader(id);
            mPreviewRequestBuilders[id] = mCameraDevices[id].createCaptureRequest(
                    CameraDevice.TEMPLATE_RECORD);
            if (true) {
                mPreviewRequestBuilders[id].set(CaptureRequest.NOISE_REDUCTION_MODE,
                        CaptureRequest.NOISE_REDUCTION_MODE_FAST);
            } else {
                mPreviewRequestBuilders[id].set(CaptureRequest.NOISE_REDUCTION_MODE,
                        CaptureRequest.NOISE_REDUCTION_MODE_HIGH_QUALITY);
            }
            List<Surface> surfaces = new ArrayList<>();

            // Set up Surface for the camera preview
            Surface previewSurface = mMultiCameraUI.getSurfaceViewList().get(index).getHolder()
                    .getSurface();
            surfaces.add(previewSurface);
            mPreviewRequestBuilders[id].addTarget(previewSurface);

            // Set up Surface for the MediaRecorder
            Surface recorderSurface = mMediaRecorders[id].getSurface();
            surfaces.add(recorderSurface);
            mPreviewRequestBuilders[id].addTarget(recorderSurface);
            surfaces.add(mImageReaders[id].getSurface());

            // Start a capture session
            // Once the session starts, we can update the UI and start recording
            mCameraDevices[id].createCaptureSession(surfaces, new CameraCaptureSession.StateCallback() {

                @Override
                public void onConfigured(CameraCaptureSession cameraCaptureSession) {
                    mCameraPreviewSessions[id] = cameraCaptureSession;
                    updatePreview(id);
                    mActivity.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            mIsRecordingVideos[id] = true;
                            // Start recording
                            mMediaRecorders[id].start();
                            requestAudioFocus();
                            mRecordingTotalTime = 0L;
                            mRecordingStartTime = SystemClock.uptimeMillis();
                            mMediaRecorderPausing = false;
                            mMultiCameraUI.resetPauseButton();
                            mMultiCameraUI.showRecordingUI(true);
                            updateRecordingTime(id);
                            keepScreenOn();
                            Log.v(TAG, " startRecordingVideo done " + id);
                        }
                    });
                }

                @Override
                public void onConfigureFailed(CameraCaptureSession cameraCaptureSession) {
                    if (null != mActivity) {
                        Toast.makeText(mActivity, "Configure Failed", Toast.LENGTH_SHORT).show();
                    }
                }
            }, mMultiCameraModule.getMyCameraHandler());
        } catch (CameraAccessException | IllegalStateException | IOException e) {
            e.printStackTrace();
        }
    }

    public void stopRecordingVideo(int id) {
        Log.v(TAG, " stopRecordingVideo " + id);
        mIsRecordingVideos[id] = false;
        try {
            mMediaRecorders[id].setOnErrorListener(null);
            mMediaRecorders[id].setOnInfoListener(null);
            // Stop recording
            mMediaRecorders[id].stop();
            mMediaRecorders[id].reset();
            saveVideo(id);
            keepScreenOnAwhile();
            // release media recorder
            releaseMediaRecorder(id);
            releaseAudioFocus();
        } catch (RuntimeException e) {
            Log.w(TAG, "MediaRecoder stop fail", e);
            if (mVideoFilenames[id] != null) deleteVideoFile(mVideoFilenames[id]);
        }

        mMultiCameraUI.showRecordingUI(false);
        if (null != mActivity) {
            Toast.makeText(mActivity, "Video saved: " + mNextVideoAbsolutePaths[id],
                    Toast.LENGTH_SHORT).show();
            Log.d(TAG, "Video saved: " + mNextVideoAbsolutePaths[id]);
        }
        mNextVideoAbsolutePaths[id] = null;
        if(!mPaused) {
            createCameraPreviewSession(id);
        }
    }

    private final MediaSaveService.OnMediaSavedListener mOnVideoSavedListener =
            new MediaSaveService.OnMediaSavedListener() {
                @Override
                public void onMediaSaved(Uri uri) {
                    if (uri != null) {
                        mActivity.notifyNewMedia(uri);
                        mCurrentVideoUri = uri;
                    }
                }
    };

    private void keepScreenOn() {
        mMultiCameraModule.getMainHandler().removeMessages(MultiCameraModule.CLEAR_SCREEN_DELAY);
        mActivity.getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

    private void keepScreenOnAwhile() {
        mMultiCameraModule.getMainHandler().removeMessages(MultiCameraModule.CLEAR_SCREEN_DELAY);
        mActivity.getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        mMultiCameraModule.getMainHandler().sendEmptyMessageDelayed(
                MultiCameraModule.CLEAR_SCREEN_DELAY, SCREEN_DELAY);
    }

    private void releaseMediaRecorder(int id) {
        Log.v(TAG, "Releasing media recorder.");
        cleanupEmptyFile(id);
        if (mMediaRecorders[id] != null) {
            try{
                mMediaRecorders[id].reset();
                mMediaRecorders[id].release();
            }catch (RuntimeException e) {
                e.printStackTrace();
            }
            mMediaRecorders[id] = null;
        }
    }

    /*
     * Make sure we're not recording music playing in the background, ask the
     * MediaPlaybackService to pause playback.
     */
    private void requestAudioFocus() {
        AudioManager am = (AudioManager)mActivity.getSystemService(Context.AUDIO_SERVICE);
        // Send request to obtain audio focus. This will stop other
        // music stream.
        int result = am.requestAudioFocus(null, AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN_TRANSIENT);
        if (result == AudioManager.AUDIOFOCUS_REQUEST_FAILED) {
            Log.v(TAG, "Audio focus request failed");
        }
    }

    private void releaseAudioFocus() {
        AudioManager am = (AudioManager)mActivity.getSystemService(Context.AUDIO_SERVICE);
        int result = am.abandonAudioFocus(null);
        if (result == AudioManager.AUDIOFOCUS_REQUEST_FAILED) {
            Log.v(TAG, "Audio focus release failed");
        }
    }

    private void cleanupEmptyFile(int id) {
        if (mVideoFilenames[id] != null) {
            File f = new File(mVideoFilenames[id]);
            if (f.length() == 0 && f.delete()) {
                Log.v(TAG, "Empty video file deleted: " + mVideoFilenames[id]);
                mVideoFilenames[id] = null;
            }
        }
    }

    private void updateMaxVideoDuration() {
        String defaultValue = mActivity.getResources().getString(
                R.string.pref_camera_video_duration_default);
        String minutesStr = mLocalSharedPref.getString(MultiSettingsActivity.KEY_VIDEO_DURATION,
                defaultValue);
        int minutes = Integer.parseInt(minutesStr);
        if (minutes == -1) {
            // User wants lowest, set 30s */
            mMaxVideoDurationInMs = 30000;
        } else {
            // 1 minute = 60000ms
            mMaxVideoDurationInMs = 60000 * minutes;
        }
    }

    private void updateAudioEncoder() {
        String audioEncoderStr = mActivity.getResources().getString(
                R.string.pref_camera_audioencoder_default);
        if (mLocalSharedPref != null) {
            audioEncoderStr = mLocalSharedPref.getString(MultiSettingsActivity.KEY_AUDIO_ENCODER,
                    audioEncoderStr);
        }
        mAudioEncoder = SettingTranslation.getAudioEncoder(audioEncoderStr);
    }

    private void updateVideoRotation() {
        String defaultValue = mActivity.getResources().getString(
                R.string.pref_camera_video_rotation_default);
        if (mLocalSharedPref != null) {
            mVideoRotation = mLocalSharedPref.getString(MultiSettingsActivity.KEY_VIDEO_ROTATION,
                    defaultValue);
        }
    }

    private void deleteVideoFile(String fileName) {
        Log.v(TAG, "Deleting video " + fileName);
        File f = new File(fileName);
        if (!f.delete()) {
            Log.v(TAG, "Could not delete " + fileName);
        }
    }

    private void saveVideo(int id) {
        File origFile = new File(mVideoFilenames[id]);
        if (!origFile.exists() || origFile.length() <= 0) {
            Log.e(TAG, "Invalid file");
            mCurrentVideoValues[id] = null;
            return;
        }

        long duration = 0L;
        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        try {
            retriever.setDataSource(mVideoFilenames[id]);
            duration = Long.valueOf(retriever.extractMetadata(
                    MediaMetadataRetriever.METADATA_KEY_DURATION));
        } catch (IllegalArgumentException e) {
            Log.e(TAG, "cannot access the file");
        }
        retriever.release();
        mActivity.getMediaSaveService().addVideo(mVideoFilenames[id],
                duration, mCurrentVideoValues[id],
                mOnVideoSavedListener, mContentResolver);
        Log.v(TAG, "saveVideo mVideoFilenames[id] :" + mVideoFilenames[id]);
        mCurrentVideoValues[id] = null;
    }

    private void updateRecordingTime(int id) {
        if (!mIsRecordingVideos[id] || id == 0) {
            return;
        }

        if (mMediaRecorderPausing) {
            return;
        }

        long now = SystemClock.uptimeMillis();
        long delta = now - mRecordingStartTime + mRecordingTotalTime;

        // Starting a minute before reaching the max duration
        // limit, we'll countdown the remaining time instead.
        boolean countdownRemainingTime = (mMaxVideoDurationInMs != 0
                && delta >= mMaxVideoDurationInMs - 60000);

        long deltaAdjusted = delta;
        if (countdownRemainingTime) {
            deltaAdjusted = Math.max(0, mMaxVideoDurationInMs - deltaAdjusted) + 999;
        }
        String text;
        long targetNextUpdateDelay;
        if (!mCaptureTimeLapse) {
            text = CameraUtil.millisecondToTimeString(deltaAdjusted, false);
            targetNextUpdateDelay = 1000;
        } else {
            // The length of time lapse video is different from the length
            // of the actual wall clock time elapsed. Display the video length
            // only in format hh:mm:ss.dd, where dd are the centi seconds.
            text = CameraUtil.millisecondToTimeString(getTimeLapseVideoLength(delta), true);
            targetNextUpdateDelay = mTimeBetweenTimeLapseFrameCaptureMs;
        }
        mMultiCameraUI.setRecordingTime(text);
        if (mRecordingTimeCountsDown != countdownRemainingTime) {
            // Avoid setting the color on every update, do it only
            // when it needs changing.
            mRecordingTimeCountsDown = countdownRemainingTime;

            int color = mActivity.getResources().getColor(countdownRemainingTime
                    ? R.color.recording_time_remaining_text
                    : R.color.recording_time_elapsed_text);

            mMultiCameraUI.setRecordingTimeTextColor(color);
        }
        long actualNextUpdateDelay = targetNextUpdateDelay - (delta % targetNextUpdateDelay);
        mMultiCameraModule.getMainHandler().postDelayed(new Runnable() {
            @Override
            public void run() {
                updateRecordingTime(id);
            }
        }, actualNextUpdateDelay);
    }

    private long getTimeLapseVideoLength(long deltaMs) {
        // For better approximation calculate fractional number of frames captured.
        // This will update the video time at a higher resolution.
        double numberOfFrames = (double) deltaMs / mTimeBetweenTimeLapseFrameCaptureMs;
        return (long) (numberOfFrames / mProfile.videoFrameRate * 1000);
    }

    /**
     * Update the camera preview. {@link #startPreview()} needs to be called in advance.
     */
    private void updatePreview(int id) {
        if (null == mCameraDevices[id]) {
            return;
        }
        try {
            setUpCaptureRequestBuilder(mPreviewRequestBuilders[id]);
            mCameraPreviewSessions[id].setRepeatingRequest(mPreviewRequestBuilders[id].build(),
                    null, mMultiCameraModule.getMyCameraHandler());
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private void setUpCaptureRequestBuilder(CaptureRequest.Builder builder) {
        builder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO);
    }

    private String generateVideoFilename(int outputFileFormat, int id) {
        long dateTaken = System.currentTimeMillis();
        String title = createName(dateTaken);
        String filename = title + "_"+ id + CameraUtil.convertOutputFormatToFileExt(outputFileFormat);
        String mime = CameraUtil.convertOutputFormatToMimeType(outputFileFormat);
        String path;
        if (Storage.isSaveSDCard() && SDCard.instance().isWriteable()) {
            path = SDCard.instance().getDirectory() + '/' + filename;
        } else {
            path = Storage.DIRECTORY + '/' + filename;
        }
        mCurrentVideoValues[id] = new ContentValues(9);
        mCurrentVideoValues[id].put(MediaStore.Video.Media.TITLE, title);
        mCurrentVideoValues[id].put(MediaStore.Video.Media.DISPLAY_NAME, filename);
        mCurrentVideoValues[id].put(MediaStore.Video.Media.DATE_TAKEN, dateTaken);
        mCurrentVideoValues[id].put(MediaStore.MediaColumns.DATE_MODIFIED, dateTaken / 1000);
        mCurrentVideoValues[id].put(MediaStore.Video.Media.MIME_TYPE, mime);
        mCurrentVideoValues[id].put(MediaStore.Video.Media.DATA, path);
        mCurrentVideoValues[id].put(MediaStore.Video.Media.RESOLUTION,
                "" + mVideoSize[id].getWidth() + "x" + mVideoSize[id].getHeight());
        Location loc = mLocationManager.getCurrentLocation();
        if (loc != null) {
            mCurrentVideoValues[id].put(MediaStore.Video.Media.LATITUDE, loc.getLatitude());
            mCurrentVideoValues[id].put(MediaStore.Video.Media.LONGITUDE, loc.getLongitude());
        }
        mVideoFilenames[id] = path;
        return path;
    }

    private String createName(long dateTaken) {
        Date date = new Date(dateTaken);
        SimpleDateFormat dateFormat = new SimpleDateFormat(
                mActivity.getString(R.string.video_file_name_format));
        return dateFormat.format(date);
    }

    private void setUpMediaRecorder(int id) throws IOException {
        if (null == mActivity) {
            return;
        }
        Log.v(TAG, " setUpMediaRecorder " + id);
        int size = CameraSettings.VIDEO_QUALITY_TABLE.get(mVideoSize[id].getWidth() + "x"
                + mVideoSize[id].getHeight());
        if (CamcorderProfile.hasProfile(id, size)) {
            mProfile = CamcorderProfile.get(id, size);
        } else {
            warningToast(R.string.error_app_unsupported_profile);
            throw new IllegalArgumentException("error_app_unsupported_profile");
        }

        if (mMediaRecorders[id] == null) {
            mMediaRecorders[id] = new MediaRecorder();
        }
        mMediaRecorders[id].setAudioSource(MediaRecorder.AudioSource.MIC);
        mMediaRecorders[id].setVideoSource(MediaRecorder.VideoSource.SURFACE);
        mMediaRecorders[id].setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
        if (mNextVideoAbsolutePaths[id] == null || mNextVideoAbsolutePaths[id].isEmpty()) {
            mNextVideoAbsolutePaths[id] = generateVideoFilename(mProfile.fileFormat, id);
        }

        mMediaRecorders[id].setMaxDuration(mMaxVideoDurationInMs);
        mMediaRecorders[id].setOutputFile(mNextVideoAbsolutePaths[id]);
        mMediaRecorders[id].setVideoEncodingBitRate(10000000);
        mMediaRecorders[id].setVideoFrameRate(30);
        mMediaRecorders[id].setVideoSize(mVideoSize[id].getWidth(), mVideoSize[id].getHeight());
        mMediaRecorders[id].setVideoEncoder(MediaRecorder.VideoEncoder.H264);
        mMediaRecorders[id].setAudioEncoder(mAudioEncoder);
        int rotation = CameraUtil.getJpegRotation(id, mOrientation);
        if (mVideoRotation != null) {
            rotation += Integer.parseInt(mVideoRotation);
            rotation = rotation % 360;
        }
        mMediaRecorders[id].setOrientationHint(rotation);
        mMediaRecorders[id].prepare();
        mMediaRecorders[id].setOnErrorListener(this);
        mMediaRecorders[id].setOnInfoListener(this);
    }

    private void warningToast(final String msg) {
        mActivity.runOnUiThread(new Runnable() {
            public void run() {
                RotateTextToast.makeText(mActivity, msg,
                        Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void warningToast(final int sourceId) {
        warningToast(sourceId, true);
    }

    private void warningToast(final int sourceId, boolean isLongShow) {
        mActivity.runOnUiThread(new Runnable() {
            public void run() {
                RotateTextToast.makeText(mActivity, sourceId,
                        isLongShow ? Toast.LENGTH_LONG : Toast.LENGTH_SHORT).show();
            }
        });
    }

    /**
     * Shows a {@link Toast} on the UI thread.
     * @param text The message to show
     */
    private void showToast(final String text) {
        if (mActivity != null) {
            mActivity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(mActivity, text, Toast.LENGTH_SHORT).show();
                }
            });
        }
    }
}
