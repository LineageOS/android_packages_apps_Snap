/*
Copyright (c) 2017, The Linux Foundation. All rights reserved.

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
package com.android.camera.imageprocessor.filter;

import android.graphics.Rect;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.TotalCaptureResult;
import android.os.Handler;
import android.util.Log;

import com.android.camera.CaptureModule;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

public class DeepZoomFilter implements ImageFilter{
    public static final int NUM_REQUIRED_IMAGE = 1;
    private int mWidth;
    private int mHeight;
    private int mStrideY;
    private int mStrideVU;
    private static String TAG = "DeepZoomFilter";

    private static boolean mIsSupported = false;
    private ByteBuffer mOutBuf;
    private CaptureModule mModule;
    private int mImageNum = -1;

    private static void Log(String msg) {
        if(DEBUG) {
            Log.d(TAG, msg);
        }
    }

    public DeepZoomFilter(CaptureModule module) {
        mModule = module;
    }

    @Override
    public List<CaptureRequest> setRequiredImages(CaptureRequest.Builder builder) {
        List<CaptureRequest> list = new ArrayList<CaptureRequest>();
        for(int i=0; i < NUM_REQUIRED_IMAGE; i++) {
            list.add(builder.build());
        }
        return list;
    }

    @Override
    public String getStringName() {
        return TAG;
    }

    @Override
    public int getNumRequiredImage() {
        return NUM_REQUIRED_IMAGE;
    }

    @Override
    public void init(int width, int height, int strideY, int strideVU) {
        Log("init");
        mWidth = width/2*2;
        mHeight = height/2*2;
        mStrideY = strideY/2*2;
        mStrideVU = strideVU/2*2;
        mOutBuf = ByteBuffer.allocate(mStrideY*mHeight*3/2);
        mImageNum = -1;
        Log("width: "+mWidth+" height: "+mHeight+" strideY: "+mStrideY+" strideVU: "+mStrideVU);
        nativeInit(mWidth, mHeight, mStrideY, mStrideVU,
                0, 0, mWidth, mHeight, NUM_REQUIRED_IMAGE);
    }

    @Override
    public void deinit() {
        Log("deinit");
        mOutBuf = null;
        mImageNum = -1;
        nativeDeinit();
    }

    @Override
    public void addImage(ByteBuffer bY, ByteBuffer bVU, int imageNum, Object param) {
        Log("addImage imageNum :" + imageNum);
        int yActualSize = bY.remaining();
        int vuActualSize = bVU.remaining();
        mImageNum = imageNum;
        int status = nativeAddImage(bY, bVU, yActualSize, vuActualSize, imageNum);
        Log("addImage status :" + status);
        if(status != 0) {
            Log.e(TAG, "Fail to add image");
        }
    }

    @Override
    public ResultImage processImage() {
        float zoomValue = mModule.getZoomValue();
        Log("processImage zoomValue :" + zoomValue);
        int[] roi = new int[4];
        int status = nativeProcessImage(mOutBuf.array(), zoomValue, roi);
        Log("processImage done status :" + status);
        mImageNum = -1;
        if(status < 0) { //In failure case, library will return the first image as it is.
            Log.w(TAG, "Fail to process the image.");
        }
        return new ResultImage(mOutBuf, new Rect(roi[0], roi[1], roi[0]+roi[2], roi[1] + roi[3]), mWidth, mHeight, mStrideY);
    }

    @Override
    public boolean isSupported() {
        return mIsSupported;
    }

    @Override
    public boolean isFrameListener() {
        return false;
    }

    @Override
    public boolean isManualMode() {
        return false;
    }

    @Override
    public void manualCapture(final CaptureRequest.Builder builder, final CameraCaptureSession captureSession,
                              final CameraCaptureSession.CaptureCallback callback, final Handler handler) throws CameraAccessException  {
    }

    public static boolean isSupportedStatic() {
        return mIsSupported;
    }

    private native int nativeInit(int width, int height, int yStride, int vuStride,
                                  int roiX, int roiY, int roiW, int roiH, int numImages);
    private native int nativeDeinit();
    private native int nativeAddImage(ByteBuffer yB, ByteBuffer vuB, int ySize, int vuSize, int imageNum);
    private native int nativeProcessImage(byte[] buffer, float zoomValue, int[] roi);

    static {
        try {
            if (mIsSupported) {
                System.loadLibrary("jni_deepzoom");
            }
            mIsSupported = false;
        }catch(UnsatisfiedLinkError e) {
            Log.d(TAG, e.toString());
            mIsSupported = false;
        }
    }
}
