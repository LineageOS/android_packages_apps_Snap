/*
 * Copyright (c) 2017, The Linux Foundation. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are
 * met:
 *  * Redistributions of source code must retain the above copyright
 *    notice, this list of conditions and the following disclaimer.
 *  * Redistributions in binary form must reproduce the above
 *    copyright notice, this list of conditions and the following
 *    disclaimer in the documentation and/or other materials provided
 *    with the distribution.
 *  * Neither the name of The Linux Foundation nor the names of its
 *    contributors may be used to endorse or promote products derived
 *    from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED "AS IS" AND ANY EXPRESS OR IMPLIED
 * WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NON-INFRINGEMENT
 * ARE DISCLAIMED.  IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS
 * BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR
 * BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
 * WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE
 * OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN
 * IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.android.camera.imageprocessor.filter;

import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Rect;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CaptureRequest;
import android.media.Image;
import android.os.AsyncTask;
import android.os.Environment;
import android.os.Handler;
import android.util.Log;
import android.util.Size;

import com.android.camera.CaptureModule;
import com.android.camera.deepportrait.CamGLRenderer;
import com.android.camera.deepportrait.DPImage;

import java.io.File;
import java.nio.ByteBuffer;
import java.util.List;

public class DeepPortraitFilter implements ImageFilter {
    private static String TAG = "DeepPortraitFilter";
    private static String VIDEO_DLC = "deepportrait_preview.dlce";
    private static String SNAPSHOT_DLC = "deepportrait_snapshot.dlce";
    private static String SD_ROOT_PATH;
    private static boolean mIsSupported = false;
    int mWidth;
    int mHeight;
    int mSnapshotWidth;
    int mSnapshotHeight;
    int mStrideY;
    int mStrideVU;
    private CaptureModule mModule;
    private CamGLRenderer mRender;
    private Boolean mDPInitialized = false;
    private Boolean mDPStillInit = false;
    private int mVideoMaskSize = 0;
    private static final int DP_QUEUE_SIZE = 30;
    private ByteBuffer[] mMaskBufArray = new ByteBuffer[DP_QUEUE_SIZE];
    private int mSeqNo;

    public DeepPortraitFilter(CaptureModule module, CamGLRenderer render) {
        mModule = module;
        mRender = render;
    }

    @Override
    public List<CaptureRequest> setRequiredImages(CaptureRequest.Builder builder) {
        return null;
    }

    @Override
    public String getStringName() {
        return null;
    }

    @Override
    public int getNumRequiredImage() {
        return 0;
    }

    @Override
    public void init(int width, int height, int strideY, int strideVU) {
        mWidth = width;
        mHeight = height;
        mStrideY = strideY;
        mStrideVU = strideVU;
        mSeqNo = 0;
        try {
            SD_ROOT_PATH = Environment.getExternalStorageDirectory().toString();
        } catch (NullPointerException e) {
            e.printStackTrace();
        }
        mDPInitialized = initPreview(width, height);
        if (mDPInitialized) {
            mVideoMaskSize = getMaskBufferSize();
            for ( int i = 0; i < mMaskBufArray.length; ++i ) {
                mMaskBufArray[i]  = ByteBuffer.allocateDirect(mVideoMaskSize);
            }
        }
        Log.d(TAG,"init width = " +width +" height = " + height);
    }

    public void initSnapshot(int width, int height) {
        if (SD_ROOT_PATH == null)
            return;
        String dlcPath = SD_ROOT_PATH + File.separator + SNAPSHOT_DLC;
        File dlc = new File(dlcPath);
        if (!dlc.exists()) {
            mDPStillInit = false;
            return;
        }
        mSnapshotWidth = width;
        mSnapshotHeight = height;
        new InitializeDpSnapShot().execute();
        Log.d(TAG,"initSnapshot width = " +width +" height = " + height);
    }

    public boolean initPreview(int width, int height) {
        if (SD_ROOT_PATH == null)
            return false;
        String dlcPath = SD_ROOT_PATH + File.separator + VIDEO_DLC;
        File dlc = new File(dlcPath);
        if (!dlc.exists()) {
            return false;
        }
        return initVideoDeepPortrait(width, height);
    }

    public boolean getDPInitialized() {
        return mDPInitialized;
    }

    public boolean getDPStillInit() {
        return mDPStillInit;
    }

    @Override
    public void deinit() {
        mDPInitialized = false;
        mDPStillInit = false;
    }

    @Override
    //inputimage is DPimage, imageNum > 0 preview ; imageNum = 0 snapshot
    public void addImage(ByteBuffer bY, ByteBuffer bVU, int imageNum, Object inputImage) {
        DPImage dpImage = (DPImage)inputImage;
        Image image = dpImage.mImage;
        Image.Plane[] planes = image.getPlanes();
        ByteBuffer bufferY = planes[0].getBuffer();
        ByteBuffer bufferC = planes[2].getBuffer();
        if (imageNum > 0) {
            mSeqNo++;
            ByteBuffer mask = mMaskBufArray[mSeqNo % mMaskBufArray.length];
            dpImage.mMask = mask;
            dpImage.mSeqNumber = mSeqNo;
            int displayOrientation = mModule.getDisplayOrientation() == -1?
                    0:mModule.getDisplayOrientation();
            int sensorOrientation = mModule.getSensorOrientation();
            int adjustedRotation = ( sensorOrientation - displayOrientation + 360 ) % 360;
            dpImage.mOrientation = adjustedRotation;
            runDpVideoWarpMask( bufferY, bufferC, planes[0].getRowStride(),
                    planes[2].getRowStride(),adjustedRotation,mask,getMaskWidth());
        } else {
            int[] maskSize = new int[2];
            boolean success = false;
            if (mDPStillInit) {
                success = getSnapshotMaskBufferSize(mSnapshotWidth,mSnapshotHeight,maskSize);
            }
            int maskWidth = maskSize[0];
            int maskHeight = maskSize[1];
            int size = maskWidth * maskHeight;
            if (!success || size == 0) {
                Log.d(TAG,"failed to get SnapshotMaskBufferSize success = "
                        + success +" size = " + size);
                return;
            }
            ByteBuffer mask = ByteBuffer.allocateDirect(maskWidth * maskHeight);
            dpImage.mMask = mask;
            dpImage.mMaskWidth = maskWidth;
            dpImage.mMaskHeight = maskHeight;
            int displayOrientation = mModule.getDisplayOrientation() == -1?
                    0:mModule.getDisplayOrientation();
            int sensorOrientation = mModule.getSensorOrientation();
            int adjustedRotation = ( sensorOrientation - displayOrientation + 360 ) % 360;
            dpImage.mOrientation = adjustedRotation;
            runDpSnapshotWarpMask(bufferY,bufferC,
                    planes[0].getRowStride(), planes[2].getRowStride(),
                    mask,maskWidth,adjustedRotation);
        }
    }

    @Override
    public ResultImage processImage() {
        return null;
    }

    public boolean renderDeepportraitImage(DPImage dpImage,ByteBuffer dstY, ByteBuffer dstVU,
                                        int effect, float intensity) {
        boolean ret;
        Image image = dpImage.mImage;
        Image.Plane[] planes = image.getPlanes();
        ByteBuffer bufferY = planes[0].getBuffer();
        ByteBuffer bufferC = planes[2].getBuffer();
        int width = image.getWidth();
        int height = image.getHeight();
        int strideY = planes[0].getRowStride();
        int strideVU = planes[2].getRowStride();
        if (dpImage.mMask == null) {
            return false;
        }
        ret = initDpEffect(bufferY,bufferC,width,height,strideY,strideVU,
                dpImage.mMask,dpImage.mMaskWidth,dpImage.mMaskHeight,dpImage.mMaskWidth);
        Log.d(TAG,"initDpEffect success = " + ret);
        if (ret) {
            ret = renderDpEffect(dstY,dstVU,width,height,strideY,strideVU,effect,intensity,
                    dpImage.mOrientation);
            Log.d(TAG,"renderDpEffect  success = " + ret);
        }
        return ret;
    }

    public static Bitmap DpMaskToImage(ByteBuffer maskBuffer, int width, int height) {
        byte[] maskArray = new byte[width * height];
        maskBuffer.get(maskArray);
        int[] rgbArray = new int[maskArray.length];
        for (int i = 0; i < maskArray.length; i++) {
            int alpha  = (int) maskArray[i];
            rgbArray[i] = Color.rgb(alpha,alpha,alpha);
        }
        Bitmap maskImage = Bitmap.createBitmap(rgbArray,width,height, Bitmap.Config.ARGB_8888);
        return maskImage;
    }

    @Override
    public boolean isSupported() {
        return mIsSupported;
    }

    public static boolean isSupportedStatic(){return mIsSupported;}

    @Override
    public boolean isFrameListener() {
        return false;
    }

    @Override
    public boolean isManualMode() {
        return false;
    }

    @Override
    public void manualCapture(CaptureRequest.Builder builder, CameraCaptureSession captureSession,
                              CameraCaptureSession.CaptureCallback callback,
                              Handler handler) throws CameraAccessException {

    }

    private class InitializeDpSnapShot extends AsyncTask<Void, Void, Void>
    {

        @Override
        protected void onPreExecute()
        {
            super.onPreExecute();
        }

        @Override
        protected void onPostExecute(Void params)
        {
            super.onPostExecute(params);
        }

        @Override
        protected Void doInBackground(Void... params)
        {

            if ( !mDPStillInit ) {
                mDPStillInit = initSnapshotDeepPortrait(mSnapshotWidth, mSnapshotHeight);
            }
            return null;
        }
    }

    public int getDpMaskWidth() {
        return getMaskWidth();
    }

    public int getDpMaskHieght() {
        return getMaskHeight();
    }


    private native boolean initVideoDeepPortrait(int width, int height);
    private native boolean initSnapshotDeepPortrait(int width, int height);
    private native boolean runDpVideoWarpMask(ByteBuffer yData, ByteBuffer vuData, int yStride,
                                              int vuStride, int orientation,
                                              ByteBuffer mask, int maskStride);
    private native boolean runDpSnapshotWarpMask(ByteBuffer yData, ByteBuffer vuData, int yStride,
                                                 int vuStride, ByteBuffer mask, int maskStride,
                                                 int orientation);
    private native boolean getSnapshotMaskBufferSize(int width, int height, int[] maskSize);
    private native int getMaskBufferSize( );
    private native int getMaskWidth( );
    private native int getMaskHeight( );
    private native boolean initDpEffect(ByteBuffer yData, ByteBuffer vuData, int width, int height,
                                        int yStride, int vuStride, ByteBuffer mask, int maskWidth,
                                        int maskHeight,int maskStride);
    private native boolean renderDpEffect(ByteBuffer dstYData, ByteBuffer dstVUData,int width,
                                          int height, int yStride, int vuStride,int effect,
                                          float intensity, int orientation);

    static {
        try {
            if (mIsSupported) {
                System.loadLibrary("jni_deepportrait");
            }
            mIsSupported = false;
        }catch(UnsatisfiedLinkError e) {
            mIsSupported = false;
            Log.d(TAG,"failed to load jni_deepportrait");
        }
    }
}
