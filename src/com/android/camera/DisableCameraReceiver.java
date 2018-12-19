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

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.hardware.Camera.CameraInfo;
import android.util.Log;
import com.android.camera.util.CameraUtil;
import org.codeaurora.snapcam.R;

// We want to disable camera-related activities if there is no camera. This
// receiver runs when BOOT_COMPLETED intent is received. After running once
// this receiver will be disabled, so it will not run again.
public class DisableCameraReceiver extends BroadcastReceiver {
    private static final String TAG = "DisableCameraReceiver";
    private static final boolean CHECK_BACK_CAMERA_ONLY = false;
    private static final String ACTIVITIES[] = {
        "com.android.camera.CameraLauncher",
    };
    private boolean mCamera2supported = false;
    private boolean mCamera2enabled = false;

    @Override
    public void onReceive(Context context, Intent intent) {
        // Check if the device supports Camera API 2
        mCamera2supported = CameraUtil.isCamera2Supported(context);
        Log.d(TAG, "Camera API 2 supported: " + mCamera2supported);

        mCamera2enabled = mCamera2supported &&
                context.getResources().getBoolean(R.bool.support_camera_api_v2);
        Log.d(TAG, "Camera API 2 enabled: " + mCamera2enabled);

        CameraHolder.setCamera2Mode(context, mCamera2enabled);

        // Disable camera-related activities if there is no camera.
        boolean needCameraActivity = CHECK_BACK_CAMERA_ONLY
            ? hasBackCamera()
            : hasCamera();

        if (!needCameraActivity) {
            Log.i(TAG, "disable all camera activities");
            for (int i = 0; i < ACTIVITIES.length; i++) {
                disableComponent(context, ACTIVITIES[i]);
            }
        }

        // Disable this receiver so it won't run again.
        disableComponent(context, "com.android.camera.DisableCameraReceiver");
    }

    private boolean hasCamera() {
        int n = CameraHolder.instance().getNumberOfCameras();
        Log.i(TAG, "number of camera: " + n);
        return (n > 0);
    }

    private boolean hasBackCamera() {
        int backCameraId = CameraHolder.instance().getBackCameraId();
        Log.i(TAG, backCameraId == -1 ? "no back camera" : ("back camera found: " + backCameraId));
        return backCameraId != -1;
    }

    private void disableComponent(Context context, String klass) {
        ComponentName name = new ComponentName(context, klass);
        PackageManager pm = context.getPackageManager();

        // We need the DONT_KILL_APP flag, otherwise we will be killed
        // immediately because we are in the same app.
        pm.setComponentEnabledSetting(name,
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
            PackageManager.DONT_KILL_APP);
    }
}
