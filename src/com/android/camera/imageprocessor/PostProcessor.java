/*
Copyright (c) 2016, The Linux Foundation. All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are
met:
    * Redistributions of source code must retain the above copyright
      notice, this list of conditions and the following disclaimer.
    * Redistributions in binary form must reproduce the above
      copyright notice, this list of conditions and the following
      disclaimer in the documentation and/or other materials provided
      with the distribution.
    * Neither the name of The Linux Foundation nor the names of its
      contributors may be used to endorse or promote products derived
      from this software without specific prior written permission.

THIS SOFTWARE IS PROVIDED "AS IS" AND ANY EXPRESS OR IMPLIED
WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NON-INFRINGEMENT
ARE DISCLAIMED.  IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS
BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR
BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE
OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN
IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.android.camera.imageprocessor;

import android.content.ContentResolver;
import android.content.Context;
import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureFailure;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.TotalCaptureResult;
import android.media.Image;
import android.media.ImageReader;
import android.media.ImageWriter;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;

import com.android.camera.CameraActivity;
import com.android.camera.CaptureModule;
import com.android.camera.Exif;
import com.android.camera.MediaSaveService;
import com.android.camera.PhotoModule;
import com.android.camera.SettingsManager;
import com.android.camera.exif.ExifInterface;
import com.android.camera.imageprocessor.filter.BestpictureFilter;
import com.android.camera.imageprocessor.filter.OptizoomFilter;
import com.android.camera.imageprocessor.filter.SharpshooterFilter;
import com.android.camera.imageprocessor.filter.StillmoreFilter;
import com.android.camera.imageprocessor.filter.UbifocusFilter;
import com.android.camera.ui.RotateTextToast;

import java.io.ByteArrayOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.TimeZone;
import java.util.concurrent.Semaphore;

import com.android.camera.imageprocessor.filter.ImageFilter;
import com.android.camera.util.CameraUtil;

public class PostProcessor{

    private CaptureModule mController;

    private static final String TAG = "PostProcessor";
    public static final int FILTER_NONE = 0;
    public static final int FILTER_OPTIZOOM = 1;
    public static final int FILTER_SHARPSHOOTER = 2;
    public static final int FILTER_UBIFOCUS = 3;
    public static final int FILTER_STILLMORE = 4;
    public static final int FILTER_BESTPICTURE = 5;
    public static final int FILTER_MAX = 6;

    //BestPicture requires 10 which is the biggest among filters
    public static final int MAX_REQUIRED_IMAGE_NUM = 11;
    private int mCurrentNumImage = 0;
    private ImageFilter mFilter;
    private int mFilterIndex;
    private HandlerThread mHandlerThread;
    private ProcessorHandler mHandler;
    private CameraActivity mActivity;
    private int mWidth;
    private int mHeight;
    private int mStride;
    private Object lock = new Object();
    private ImageFilter.ResultImage mDefaultResultImage;  //This is used only no filter is chosen.
    private Image[] mImages;
    private PhotoModule.NamedImages mNamedImages;
    private WatchdogThread mWatchdog;
    private int mOrientation = 0;
    private ImageWriter mZSLImageWriter;

    //This is for the debug feature.
    private static boolean DEBUG_FILTER = false;
    private static boolean DEBUG_ZSL = false;
    private ImageFilter.ResultImage mDebugResultImage;
    private ZSLQueue mZSLQueue;
    private CameraDevice mCameraDevice;
    private CameraCaptureSession mCaptureSession;
    private ImageReader mImageReader;
    private boolean mUseZSL = false;
    private Handler mZSLHandler;
    private HandlerThread mZSLHandlerThread;
    private ImageHandlerTask mImageHandlerTask;

    private void checkAndEnableZSL(int cameraId) {
        if (mController.mSettingsManager.isZslSupported(cameraId)) {
            mUseZSL = true;
        } else {
            mUseZSL = false;
        }
    }

    public boolean isZSLEnabled() {
        return mUseZSL;
    }

    public ImageHandlerTask getImageHandler() {
        return mImageHandlerTask;
    }

    private class ImageWrapper {
        Image mImage;
        boolean mIsTaken;

        public ImageWrapper(Image image) {
            mImage = image;
            mIsTaken = false;
        }

        public boolean isTaken() {
            return mIsTaken;
        }

        public Image getImage() {
            mIsTaken = true;
            return mImage;
        }
    }

    class ImageHandlerTask implements Runnable, ImageReader.OnImageAvailableListener {
        private ImageWrapper mImageWrapper = null;
        Semaphore mMutureLock = new Semaphore(1);

        @Override
        public void onImageAvailable(ImageReader reader) {
            try {
                Image image = reader.acquireLatestImage();
                if(image == null) {
                    return;
                }
                if (!mMutureLock.tryAcquire()) {
                    image.close();
                    return;
                }
                if(mImageWrapper == null || mImageWrapper.isTaken()) {
                    mImageWrapper = new ImageWrapper(image);
                    mMutureLock.release();
                } else {
                    image.close();
                    mMutureLock.release();
                    return;
                }
                if(mZSLHandler != null) {
                    mZSLHandler.post(this);
                }
            } catch (IllegalStateException e) {
                Log.e(TAG, "Max images has been already acquired. ");
            }
        }

        @Override
        public void run() {
            try {
                mMutureLock.acquire();
                Image image = mImageWrapper.getImage();
                if (mUseZSL) {
                    if (mZSLQueue != null) {
                        mZSLQueue.add(image);
                    }
                } else {
                    onImageToProcess(image);
                }
                mMutureLock.release();
            } catch (InterruptedException e) {
            }
        }
    }

    public void onMetaAvailable(TotalCaptureResult metadata) {
        if(mUseZSL && mZSLQueue != null) {
            mZSLQueue.add(metadata);
        }
    }

    public void onSessionConfiguredForZSL(CameraCaptureSession captureSession) {
        mZSLImageWriter = ImageWriter.newInstance(captureSession.getInputSurface(), 2);
    }

    public boolean takeZSLPicture(CameraDevice cameraDevice, CameraCaptureSession captureSession, ImageReader imageReader) {
        if(mCameraDevice == null || mCaptureSession == null || mImageReader == null) {
            mCameraDevice = cameraDevice;
            mCaptureSession = captureSession;
            mImageReader = imageReader;
        }
        ZSLQueue.ImageItem imageItem = mZSLQueue.tryToGetMatchingItem();
        if(mController.getPreviewCaptureResult().get(CaptureResult.CONTROL_AE_STATE) == CameraMetadata.CONTROL_AE_STATE_FLASH_REQUIRED) {
            if(DEBUG_ZSL) Log.d(TAG, "Flash required image");
            imageItem = null;
        }
        if (imageItem != null) {
            if(DEBUG_ZSL) Log.d(TAG,"Got the item from the queue");
            reprocessImage(imageItem);
            return true;
        } else {
            if(DEBUG_ZSL) Log.d(TAG, "No good item in queue, reigster the request for the future");
            mZSLQueue.addPictureRequest();
            return false;
        }
    }

    public void onMatchingZSLPictureAvailable(ZSLQueue.ImageItem imageItem) {
        reprocessImage(imageItem);
    }

    private void reprocessImage(ZSLQueue.ImageItem imageItem) {
        if(mCameraDevice == null || mCaptureSession == null || mImageReader == null) {
            Log.e(TAG, "Reprocess request is called even before taking picture");
            new Throwable().printStackTrace();
            return;
        }
        CaptureRequest.Builder builder = null;
        try {
            builder = mCameraDevice.createReprocessCaptureRequest(imageItem.getMetadata());
            builder.addTarget(mImageReader.getSurface());
            builder.set(CaptureRequest.COLOR_CORRECTION_ABERRATION_MODE,
                    CaptureRequest.COLOR_CORRECTION_ABERRATION_MODE_HIGH_QUALITY);
            builder.set(CaptureRequest.EDGE_MODE,
                    CaptureRequest.EDGE_MODE_HIGH_QUALITY);
            builder.set(CaptureRequest.NOISE_REDUCTION_MODE,
                    CaptureRequest.NOISE_REDUCTION_MODE_HIGH_QUALITY);
            try {
                mZSLImageWriter.queueInputImage(imageItem.getImage());
            } catch(IllegalStateException e) {
                Log.e(TAG, "Queueing more than it can have");
            }
            mCaptureSession.capture(builder.build(),
                    new CameraCaptureSession.CaptureCallback() {
                        @Override
                        public void onCaptureCompleted(
                                CameraCaptureSession session,
                                CaptureRequest request,
                                TotalCaptureResult result) {
                            super.onCaptureCompleted(session, request, result);
                            Log.d(TAG, "Success to reprocess zsl image");
                            try {
                                onImageToProcess(mZSLImageWriter.dequeueInputImage());
                            } catch(IllegalStateException e) {
                                Log.e(TAG, "Dequeueing more than what it has");
                            }
                        }

                        @Override
                        public void onCaptureFailed(
                                CameraCaptureSession session,
                                CaptureRequest request,
                                CaptureFailure failure) {
                            super.onCaptureFailed(session, request, failure);
                            Log.e(TAG, "failed to reprocess");
                        }
                    }, null);
        } catch (CameraAccessException e) {
        }
    }

    private void onImageToProcess(Image image) {
        addImage(image);
        if (isReadyToProcess()) {
            long captureStartTime = System.currentTimeMillis();
            mNamedImages.nameNewImage(captureStartTime);
            PhotoModule.NamedImages.NamedEntity name = mNamedImages.getNextNameEntity();
            String title = (name == null) ? null : name.title;
            long date = (name == null) ? -1 : name.date;
            processImage(title, date, mController.getMediaSavedListener(), mActivity.getContentResolver());
        }
    }

    enum STATUS {
        DEINIT,
        INIT,
        BUSY
    }
    private STATUS mStatus = STATUS.DEINIT;

    public PostProcessor(CameraActivity activity, CaptureModule module) {
        mController = module;
        mActivity = activity;
        checkAndEnableZSL(mController.getMainCameraId());
        mNamedImages = new PhotoModule.NamedImages();        
    }

    public boolean isItBusy() {
        if(mStatus == STATUS.BUSY)
            return true;
        return false;
    }

    public List<CaptureRequest> setRequiredImages(CaptureRequest.Builder builder) {
        if(mFilter == null) {
            List<CaptureRequest> list = new ArrayList<CaptureRequest>();
            list.add(builder.build());
            return list;
        } else {
            return mFilter.setRequiredImages(builder);
        }
    }

    public boolean isManualMode() {
        return mFilter.isManualMode();
    }

    public void manualCapture(CaptureRequest.Builder builder, CameraCaptureSession captureSession,
                              CameraCaptureSession.CaptureCallback callback, Handler handler) throws CameraAccessException {
        mFilter.manualCapture(builder, captureSession, callback, handler);
    }

    public boolean isFilterOn() {
        if (mFilter != null) {
            return true;
        }
        return false;
    }

    public void onOpen(int postFilterId, boolean isLongShotOn, boolean isFlashModeOn) {
        mImageHandlerTask = new ImageHandlerTask();

        if (mController.mSettingsManager.isZslSupported(mController.getMainCameraId())) {
             if (setFilter(postFilterId) || isLongShotOn || isFlashModeOn) {
                 mUseZSL = false;
             } else {
                 mUseZSL = true;
             }
        } else {
            mUseZSL = false;
        }

        startBackgroundThread();

        if (mUseZSL) {
            mZSLQueue = new ZSLQueue(mController);
        }
    }

    public int getFilterIndex() {
        return mFilterIndex;
    }

    public void onClose() {
        synchronized (lock) {
            if (mHandler != null) {
                mHandler.setInActive();
            }
            stopBackgroundThread();
        }
        setFilter(FILTER_NONE);
        if (mZSLQueue != null) {
            mZSLQueue.onClose();
            mZSLQueue = null;
        }
        if (mZSLImageWriter != null) {
            mZSLImageWriter.close();
            mZSLImageWriter = null;
        }
        mCameraDevice = null;
        mCaptureSession = null;
        mImageReader = null;
    }

    private void startBackgroundThread() {
        mHandlerThread = new HandlerThread("PostProcessorThread");
        mHandlerThread.start();
        mHandler = new ProcessorHandler(mHandlerThread.getLooper());

        mZSLHandlerThread = new HandlerThread("ZSLHandlerThread");
        mZSLHandlerThread.start();
        mZSLHandler = new ProcessorHandler(mZSLHandlerThread.getLooper());

        mWatchdog = new WatchdogThread();
        mWatchdog.start();
    }

    class WatchdogThread extends Thread {
        private boolean isAlive = true;
        private boolean isMonitor = false;
        private int counter = 0;
        public void run() {
            while(isAlive) {
                try {
                    Thread.sleep(200);
                }catch(InterruptedException e) {
                }
                if(isMonitor) {
                    counter++;
                    if(counter >= 40) { //This is 4 seconds.
                        bark();
                        break;
                    }
                }
            }
        }

        public void startMonitor() {
            isMonitor = true;
        }

        public void stopMonitor() {
            isMonitor = false;
            counter = 0;
        }

        public void kill() {
            isAlive = false;
        }
        private void bark() {
            Log.e(TAG, "It takes too long to get the images and process the filter!");
            int index = getFilterIndex();
            setFilter(FILTER_NONE);
            setFilter(index);
        }
    }

    class ProcessorHandler extends Handler {
        boolean isRunning;

        public ProcessorHandler(Looper looper) {
            super(looper);
            isRunning = true;
        }

        public void setInActive() {
            isRunning = false;
        }
    }

    private void stopBackgroundThread() {
        if (mHandlerThread != null) {
            mHandlerThread.quitSafely();
            try {
                mHandlerThread.join();
            } catch (InterruptedException e) {
            }
            mHandlerThread = null;
            mHandler = null;
        }
        if (mZSLHandlerThread != null) {
            mZSLHandlerThread.quitSafely();
            try {
                mZSLHandlerThread.join();
            } catch (InterruptedException e) {
            }
            mZSLHandlerThread = null;
            mZSLHandler = null;
        }
        if(mWatchdog != null) {
            mWatchdog.kill();
            mWatchdog = null;
        }
        clear();
    }

    public boolean setFilter(int index) {
        if(index < 0 || index >= FILTER_MAX) {
            Log.e(TAG, "Invalid scene filter ID");
            return false;
        }
        synchronized (lock) {
            if (mFilter != null) {
                mFilter.deinit();
            }
            mStatus = STATUS.DEINIT;
            switch (index) {
                case FILTER_NONE:
                    mFilter = null;
                    break;
                case FILTER_OPTIZOOM:
                    mFilter = new OptizoomFilter(mController);
                    break;
                case FILTER_SHARPSHOOTER:
                    mFilter = new SharpshooterFilter(mController);
                    break;
                case FILTER_UBIFOCUS:
                    mFilter = new UbifocusFilter(mController, mActivity);
                    break;
                case FILTER_STILLMORE:
                    mFilter = new StillmoreFilter(mController);
                    break;
                case FILTER_BESTPICTURE:
                    mFilter = new BestpictureFilter(mController, mActivity);
                    break;
            }
        }

        if(mFilter != null && !mFilter.isSupported()) {
            final String filterName = mFilter.getStringName();
            mFilter = null;
            mActivity.runOnUiThread(new Runnable() {
                public void run() {
                    RotateTextToast.makeText(mActivity, filterName+" is not supported. ", Toast.LENGTH_SHORT).show();
                }
            });
        }

        if(mFilter == null) {
            mFilterIndex = FILTER_NONE;
            return false;
        }
        mFilterIndex = index;
        mImages = new Image[mFilter.getNumRequiredImage()];
        return true;
    }

    private boolean isReadyToProcess() {
        synchronized (lock) {
            if (mFilter == null) {
                return true;
            }
            if (mCurrentNumImage >= mFilter.getNumRequiredImage()) {
                return true;
            }
        }
        return false;
    }

    private void addImage(final Image image) {
        if(mHandler == null || !mHandler.isRunning) {
            return;
        }
        final ProcessorHandler handler = mHandler;
        if (mStatus == STATUS.DEINIT) {
            mWidth = image.getWidth();
            mHeight = image.getHeight();
            mStride = image.getPlanes()[0].getRowStride();
            mStatus = STATUS.INIT;
            mHandler.post(new Runnable() {
                    public void run() {
                        synchronized (lock) {
                            if(!handler.isRunning) {
                                return;
                            }
                            if(mFilter == null) {
                                //Nothing here we have to do if filter is not chosen.
                            } else {
                                mFilter.init(mWidth, mHeight, mStride, mStride);
                            }
                        }
                    }
                });
        }
        if(mCurrentNumImage == 0) {
            mStatus = STATUS.BUSY;
            if(mWatchdog != null) {
                mWatchdog.startMonitor();
            }
            mOrientation = CameraUtil.getJpegRotation(mController.getMainCameraId(), mController.getDisplayOrientation());
        }
        if(mFilter != null && mCurrentNumImage >= mFilter.getNumRequiredImage()) {
            return;
        }
        final int numImage = mCurrentNumImage;
        mCurrentNumImage++;
        if(mHandler == null) {
            return;
        }
        mHandler.post(new Runnable() {
                public void run() {
                    synchronized (lock) {
                        if(!handler.isRunning) {
                            return;
                        }
                        ByteBuffer yBuf = image.getPlanes()[0].getBuffer();
                        ByteBuffer vuBuf = image.getPlanes()[2].getBuffer();
                        if(mFilter != null && DEBUG_FILTER && numImage == 0) {
                            mDebugResultImage = new ImageFilter.ResultImage(ByteBuffer.allocateDirect(mStride * mHeight*3/2),
                                    new Rect(0, 0, mWidth, mHeight), mWidth, mHeight, mStride);
                            yBuf.get(mDebugResultImage.outBuffer.array(), 0, yBuf.remaining());
                            vuBuf.get(mDebugResultImage.outBuffer.array(), mStride * mHeight, vuBuf.remaining());
                            yBuf.rewind();
                            vuBuf.rewind();
                        }
                        if(mFilter == null) {
                            mDefaultResultImage = new ImageFilter.ResultImage(ByteBuffer.allocateDirect(mStride * mHeight*3/2),
                                                                    new Rect(0, 0, mWidth, mHeight), mWidth, mHeight, mStride);
                            yBuf.get(mDefaultResultImage.outBuffer.array(), 0, yBuf.remaining());
                            vuBuf.get(mDefaultResultImage.outBuffer.array(), mStride*mHeight, vuBuf.remaining());
                            image.close();
                        } else {
                            mFilter.addImage(image.getPlanes()[0].getBuffer(),
                                    image.getPlanes()[2].getBuffer(), numImage, null);
                            mImages[numImage] = image;
                        }
                    }
                }
            });
    }

    public static byte[] addExifTags(byte[] jpeg, int orientationInDegree) {
        ExifInterface exif = new ExifInterface();
        exif.addOrientationTag(orientationInDegree);
        exif.addDateTimeStampTag(ExifInterface.TAG_DATE_TIME, System.currentTimeMillis(),
                TimeZone.getDefault());
        ByteArrayOutputStream jpegOut = new ByteArrayOutputStream();
        try {
            exif.writeExif(jpeg, jpegOut);
        } catch (IOException e) {
            Log.e(TAG, "Could not write EXIF", e);
        }
        return jpegOut.toByteArray();
    }

    private void clear() {
        mCurrentNumImage = 0;
    }

    private void processImage(final String title, final long date,
                             final MediaSaveService.OnMediaSavedListener mediaSavedListener,
                             final ContentResolver contentResolver) {
        if(mHandler == null || !mHandler.isRunning) {
            return;
        }
        final ProcessorHandler handler = mHandler;
        mHandler.post(new Runnable() {
            public void run() {
                byte[] bytes;
                ImageFilter.ResultImage resultImage = null;
                synchronized (lock) {
                    if (!handler.isRunning) {
                        return;
                    }
                    if (mFilter == null) { //In case no post filter is chosen
                        resultImage = mDefaultResultImage;
                    } else {
                        resultImage = mFilter.processImage();
                        for (int i = 0; i < mImages.length; i++) {
                            if(mImages[i] != null) {
                                mImages[i].close();
                                mImages[i] = null;
                            }
                        }
                    }
                    if(resultImage != null) {
                        //Start processing FrameProcessor filter as well
                        for (ImageFilter filter : mController.getFrameFilters()) {
                            filter.init(resultImage.width, resultImage.height, resultImage.stride, resultImage.stride);
                            filter.addImage(resultImage.outBuffer, null, 0, new Boolean(false));
                        }
                    }
                    //End processing FrameProessor filter
                    clear();
                    mStatus = STATUS.INIT;
                    if(mWatchdog != null) {
                        mWatchdog.stopMonitor();
                    }
                    if(resultImage == null ||
                            (resultImage.outRoi.left + resultImage.outRoi.width() > resultImage.width) ||
                            (resultImage.outRoi.top + resultImage.outRoi.height() > resultImage.height)
                            ) {
                        Log.d(TAG, "Result image is not valid.");
                    } else {
                        if(mFilter != null && DEBUG_FILTER) {
                            bytes = nv21ToJpeg(mDebugResultImage, mOrientation);
                            mActivity.getMediaSaveService().addImage(
                                    bytes, title + "_beforeApplyingFilter", date, null, mDebugResultImage.outRoi.width(), mDebugResultImage.outRoi.height(),
                                    mOrientation, null, mediaSavedListener, contentResolver, "jpeg");
                        }
                        bytes = nv21ToJpeg(resultImage, mOrientation);
                        mActivity.getMediaSaveService().addImage(
                                bytes, title, date, null, resultImage.outRoi.width(), resultImage.outRoi.height(),
                                mOrientation, null, mediaSavedListener, contentResolver, "jpeg");
                        mController.updateThumbnailJpegData(bytes);
                    }
                }
            }
        });
    }

    private byte[] nv21ToJpeg(ImageFilter.ResultImage resultImage, int orientation) {
        BitmapOutputStream bos = new BitmapOutputStream(1024);
        YuvImage im = new YuvImage(resultImage.outBuffer.array(), ImageFormat.NV21,
                                    resultImage.width, resultImage.height, new int[]{resultImage.stride, resultImage.stride});
        im.compressToJpeg(resultImage.outRoi, 50, bos);
        byte[] bytes = bos.getArray();
        bytes = addExifTags(bytes, orientation);
        return bytes;
    }

    private class BitmapOutputStream extends ByteArrayOutputStream {
        public BitmapOutputStream(int size) {
            super(size);
        }

        public byte[] getArray() {
            return buf;
        }
    }

    private native int nativeFlipVerticalNV21(byte[] buf, int stride, int height);

    static {
        System.loadLibrary("jni_snapimageutil");
    }
}
