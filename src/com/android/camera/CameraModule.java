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

    void init(CameraActivity activity, View frame);

    void onPreviewFocusChanged(boolean previewFocused);

    void onPauseBeforeSuper();

    void onPauseAfterSuper();

    void onResumeBeforeSuper();

    void onResumeAfterSuper();

    @SuppressWarnings("UnnecessaryInterfaceModifier")
    void onConfigurationChanged(Configuration config);

    void onStop();

    void installIntentFilter();

    void onActivityResult(int requestCode, int resultCode, Intent data);

    boolean onBackPressed();

    boolean onKeyDown(int keyCode, KeyEvent event);

    boolean onKeyUp(int keyCode, KeyEvent event);

    void onSingleTapUp(View view, int x, int y);

    void onPreviewTextureCopied();

    void onCaptureTextureCopied();

    void onUserInteraction();

    boolean updateStorageHintOnResume();

    void onOrientationChanged(int orientation);

    void onShowSwitcherPopup();

    void onMediaSaveServiceConnected(MediaSaveService s);

    boolean arePreviewControlsVisible();

    void resizeForPreviewAspectRatio();

    void onSwitchSavePath();
}

