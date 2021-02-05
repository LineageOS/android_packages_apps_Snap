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

import android.content.Intent;
import android.content.res.Configuration;
import android.view.KeyEvent;
import android.view.View;

public interface CameraModule {

    public void init(CameraActivity activity, View frame);

    public void onPreviewFocusChanged(boolean previewFocused);

    public void onPauseBeforeSuper();

    public void onPauseAfterSuper();

    public void onResumeBeforeSuper();

    public void onResumeAfterSuper();

    public void onConfigurationChanged(Configuration config);

    public void onStop();

    public void onDestroy();

    public void installIntentFilter();

    public void onActivityResult(int requestCode, int resultCode, Intent data);

    public boolean onBackPressed();

    public boolean onKeyDown(int keyCode, KeyEvent event);

    public boolean onKeyUp(int keyCode, KeyEvent event);

    public void onSingleTapUp(View view, int x, int y);

    public void onPreviewTextureCopied();

    public void onCaptureTextureCopied();

    public void onUserInteraction();

    public boolean updateStorageHintOnResume();

    public void onOrientationChanged(int orientation);

    public void onShowSwitcherPopup();

    public void onMediaSaveServiceConnected(MediaSaveService s);

    public boolean arePreviewControlsVisible();

    public void resizeForPreviewAspectRatio();

    public void onSwitchSavePath();

    public void waitingLocationPermissionResult(boolean waiting);

    public void enableRecordingLocation(boolean enable);

    public void setPreferenceForTest(String key, String value);
}
