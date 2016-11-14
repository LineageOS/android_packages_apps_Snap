/*
 * Copyright (C) 2016 The CyanogenMod Project
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

public abstract class BaseModule<T extends BaseUI> implements CameraModule {

    // subclass initializes in init() for now
    protected T mUI;

    @Override
    public void showPreviewCover() {
        mUI.showPreviewCover();
    }

    @Override
    public void hidePreviewCover() {
        mUI.hidePreviewCover();
    }

    @Override
    public void animateControls(float alpha) {
        mUI.animateControls(alpha);
    }

    @Override
    public boolean arePreviewControlsVisible() {
        return mUI.arePreviewControlsVisible();
    }

    @Override
    public void onPreviewFocusChanged(boolean previewFocused) {
        mUI.onPreviewFocusChanged(previewFocused);
    }
}
