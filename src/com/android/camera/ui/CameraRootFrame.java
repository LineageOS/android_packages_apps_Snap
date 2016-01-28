/*
 * Copyright (C) 2013 The Android Open Source Project
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

package com.android.camera.ui;

import android.content.Context;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.widget.FrameLayout;

public class CameraRootFrame extends FrameLayout {
    private Rect mLastInsets = new Rect();

    public CameraRootFrame(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public void redoFitSystemWindows() {
        if (mLastInsets.left != 0 || mLastInsets.right != 0
                || mLastInsets.top != 0 || mLastInsets.bottom != 0) {
            Rect insets = new Rect(mLastInsets);
            fitSystemWindows(insets);
        }
    }

    @Override
    protected boolean fitSystemWindows(Rect insets) {
        mLastInsets.set(insets);
        return super.fitSystemWindows(insets);
    }
}
