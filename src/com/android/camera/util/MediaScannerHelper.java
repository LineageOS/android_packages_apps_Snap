/*
 * Copyright (C) 2021 SHIFT GmbH
 * Copyright (C) 2021 The LineageOS Project
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

package com.android.camera.util;

import android.content.Context;
import android.media.MediaScannerConnection;
import android.util.Log;

import com.android.camera.app.CameraApp;

public class MediaScannerHelper {

    private static final String TAG = "MediaScannerHelper";
    private static final boolean DEBUG = false;

    private MediaScannerHelper() {
        // do not instantiate
    }

    public static void scanFile(final String filePath, final String mimeType) {
        final Context context = CameraApp.getContext();
        final String[] filePaths = new String[]{filePath};
        final String[] mimeTypes = new String[]{mimeType};

        MediaScannerConnection.scanFile(context, filePaths, mimeTypes, (path, uri) -> {
            if (DEBUG) {
                Log.d(TAG, "onScanCompleted(" + path + ", " + uri + ")");
            }
        });
    }
}
