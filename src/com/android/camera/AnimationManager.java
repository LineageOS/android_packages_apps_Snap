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

package com.android.camera;

import android.animation.Animator;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.graphics.Color;
import android.graphics.Paint;
import android.view.View;
import android.view.animation.LinearInterpolator;

/**
 * Class to handle animations.
 */

public class AnimationManager {

    private static final float FLASH_MAX_ALPHA = 0.85f;
    private static final long FLASH_FULL_DURATION_MS = 65;
    private static final long FLASH_DECREASE_DURATION_MS = 150;
    private static final float SHORT_FLASH_MAX_ALPHA = 0.75f;
    private static final long SHORT_FLASH_FULL_DURATION_MS = 34;
    private static final long SHORT_FLASH_DECREASE_DURATION_MS = 100;

    public static final int SHRINK_DURATION = 400;
    public static final int HOLD_DURATION = 2500;
    public static final int SLIDE_DURATION = 1100;

    private AnimatorSet mCaptureAnimator;

    private final Paint mPaint = new Paint();
    private AnimatorSet mFlashAnimation;
    private final LinearInterpolator mFlashAnimInterpolator;
    private final Animator.AnimatorListener mFlashAnimListener;

    private final View mFlashView;

    public AnimationManager(View flashView) {
        mFlashView = flashView;

        mPaint.setColor(Color.WHITE);
        mFlashAnimInterpolator = new LinearInterpolator();
        mFlashAnimListener = new Animator.AnimatorListener() {
            @Override
            public void onAnimationStart(Animator animation) {
                mFlashView.setVisibility(View.VISIBLE);
            }

            @Override
            public void onAnimationEnd(Animator animation) {
                mFlashView.setAlpha(0f);
                mFlashView.setVisibility(View.GONE);
                mFlashAnimation.removeAllListeners();
                mFlashAnimation = null;
            }

            @Override
            public void onAnimationCancel(Animator animation) {
                // End is always called after cancel.
            }

            @Override
            public void onAnimationRepeat(Animator animation) {

            }
        };
    }

    /**
     * Starts capture animation.
     * @param view a thumbnail view that shows a picture captured and gets animated
     */
    public void startCaptureAnimation(final View view) {
        if (mCaptureAnimator != null && mCaptureAnimator.isStarted()) {
            mCaptureAnimator.cancel();
        }
        View parentView = (View) view.getParent();
        float slideDistance = (float) (parentView.getWidth() - view.getLeft());

        float scaleX = ((float) parentView.getWidth()) / ((float) view.getWidth());
        float scaleY = ((float) parentView.getHeight()) / ((float) view.getHeight());
        float scale = scaleX > scaleY ? scaleX : scaleY;

        int centerX = view.getLeft() + view.getWidth() / 2;
        int centerY = view.getTop() + view.getHeight() / 2;

        ObjectAnimator slide = ObjectAnimator.ofFloat(view, "translationX", 0f, slideDistance)
                .setDuration(AnimationManager.SLIDE_DURATION);
        slide.setStartDelay(AnimationManager.SHRINK_DURATION + AnimationManager.HOLD_DURATION);

        ObjectAnimator translateY = ObjectAnimator.ofFloat(view, "translationY",
                parentView.getHeight() / 2 - centerY, 0f)
                .setDuration(AnimationManager.SHRINK_DURATION);
        translateY.addListener(new Animator.AnimatorListener() {
            @Override
            public void onAnimationStart(Animator animator) {
                // Do nothing.
            }

            @Override
            public void onAnimationEnd(Animator animator) {
                view.setClickable(true);
            }

            @Override
            public void onAnimationCancel(Animator animator) {
                // Do nothing.
            }

            @Override
            public void onAnimationRepeat(Animator animator) {
                // Do nothing.
            }
        });

        mCaptureAnimator = new AnimatorSet();
        mCaptureAnimator.playTogether(
                ObjectAnimator.ofFloat(view, "scaleX", scale, 1f)
                        .setDuration(AnimationManager.SHRINK_DURATION),
                ObjectAnimator.ofFloat(view, "scaleY", scale, 1f)
                        .setDuration(AnimationManager.SHRINK_DURATION),
                ObjectAnimator.ofFloat(view, "translationX",
                        parentView.getWidth() / 2 - centerX, 0f)
                        .setDuration(AnimationManager.SHRINK_DURATION),
                translateY,
                slide);
        mCaptureAnimator.addListener(new Animator.AnimatorListener() {
            @Override
            public void onAnimationStart(Animator animator) {
                view.setClickable(false);
                view.setVisibility(View.VISIBLE);
            }

            @Override
            public void onAnimationEnd(Animator animator) {
                view.setScaleX(1f);
                view.setScaleX(1f);
                view.setTranslationX(0f);
                view.setTranslationY(0f);
                view.setVisibility(View.INVISIBLE);
                mCaptureAnimator.removeAllListeners();
                mCaptureAnimator = null;
            }

            @Override
            public void onAnimationCancel(Animator animator) {
                view.setVisibility(View.INVISIBLE);
            }

            @Override
            public void onAnimationRepeat(Animator animator) {
                // Do nothing.
            }
        });
        mCaptureAnimator.start();
    }

   /**
    * Starts flash animation.
    * @params flashOverlay the overlay that will animate on alpha to make the flash impression
    */
    public void startFlashAnimation(boolean shortFlash) {
        if (mFlashAnimation != null && mFlashAnimation.isRunning()) {
            mFlashAnimation.cancel();
        }

        float maxAlpha;

        if (shortFlash) {
            maxAlpha = SHORT_FLASH_MAX_ALPHA;
        } else {
            maxAlpha = FLASH_MAX_ALPHA;
        }

        ValueAnimator flashAnim1 = ObjectAnimator.ofFloat(mFlashView, "alpha", maxAlpha, maxAlpha);
        ValueAnimator flashAnim2 = ObjectAnimator.ofFloat(mFlashView, "alpha", maxAlpha, .0f);

        if (shortFlash) {
            flashAnim1.setDuration(SHORT_FLASH_FULL_DURATION_MS);
            flashAnim2.setDuration(SHORT_FLASH_DECREASE_DURATION_MS);
        } else {
            flashAnim1.setDuration(FLASH_FULL_DURATION_MS);
            flashAnim2.setDuration(FLASH_DECREASE_DURATION_MS);
        }

        flashAnim1.setInterpolator(mFlashAnimInterpolator);
        flashAnim2.setInterpolator(mFlashAnimInterpolator);

        mFlashAnimation = new AnimatorSet();
        mFlashAnimation.play(flashAnim1).before(flashAnim2);
        mFlashAnimation.addListener(mFlashAnimListener);
        mFlashAnimation.start();
    }

    /**
     * Cancels on-going flash animation and capture animation, if any.
     */
    public void cancelAnimations() {
        // End the previous animation if the previous one is still running
        if (mFlashAnimation != null && mFlashAnimation.isRunning()) {
            mFlashAnimation.cancel();
        }
        if (mCaptureAnimator != null && mCaptureAnimator.isStarted()) {
            mCaptureAnimator.cancel();
        }
    }
}
