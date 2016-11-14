/*
 * Copyright (C) 2016  The CyanogenMod Project
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
import android.os.Handler;
import android.os.Message;
import android.os.SystemClock;
import android.transition.*;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.widget.TextView;

import com.android.camera.PauseButton;

import org.codeaurora.snapcam.R;

public class RecordingTime extends RotateLayout implements PauseButton.OnPauseButtonListener {

    private static final String TAG = "CAM_" + RecordingTime.class.getSimpleName();

    private PauseButton mPauseButton;
    private TextView mRecordingTimeText;
    private TextView mTimeLapseLabel;
    private ReversibleLinearLayout mRecordingTimeContainer;

    private PauseButton.OnPauseButtonListener mPauseListener;

    private boolean mPaused = false;
    private boolean mStarted = false;
    private boolean mTimeLapse = false;

    private long mRecordingStartTime;
    private long mRecordingTotalTime;

    private int mFrameRate = 0;
    private long mInterval = 0;
    private long mDurationMs = 0;
    private boolean mRecordingTimeCountsDown = false;

    private static final int UPDATE_RECORD_TIME = 0;

    private final Handler mHandler = new Handler() {

        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case UPDATE_RECORD_TIME:
                    updateRecordingTime();
                    break;
                default:
                    break;
            }
        }
    };

    public RecordingTime(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        mPauseButton = (PauseButton) findViewById(R.id.video_pause);
        mRecordingTimeText = (TextView) findViewById(R.id.recording_time_text);
        mRecordingTimeContainer = (ReversibleLinearLayout) findViewById(R.id.recording_time_container);
        mTimeLapseLabel = (TextView) findViewById(R.id.time_lapse_label);
        mPauseButton.setOnPauseButtonListener(this);
        setAlpha(0.0f);
    }

    public void setOrientation(int orientation) {
        if (mRecordingTimeText != null) {
            Log.d(TAG, "orientation=" + orientation);
            setRotation(orientation);
            float topBar = getContext().getResources().getDimension(R.dimen.preview_top_margin);

            if (orientation == 0 || orientation == 270) {
                mRecordingTimeContainer.setOrder(ReversibleLinearLayout.FORWARD);
            } else {
                mRecordingTimeContainer.setOrder(ReversibleLinearLayout.REVERSE);
            }

            if (orientation == 0 || orientation == 180) {
                setTranslationX(0);
                setTranslationY(0);
                mRecordingTimeText.setRotation(0);
                if (mTimeLapse) {
                    mTimeLapseLabel.setRotation(0);
                }
            } else {
                setTranslationX(-topBar);
                setTranslationY(topBar);
                mRecordingTimeText.setRotation(180);
                if (mTimeLapse) {
                    mTimeLapseLabel.setRotation(180);
                }
            }
        }
    }

    @Override
    public void onButtonPause() {
        mPaused = true;
        mRecordingTotalTime += SystemClock.uptimeMillis() - mRecordingStartTime;

        mRecordingTimeText.setCompoundDrawablesWithIntrinsicBounds(
                R.drawable.ic_pausing_indicator, 0, 0, 0);
        if (mPauseListener != null) {
            mPauseListener.onButtonPause();
        }
    }

    @Override
    public void onButtonContinue() {
        mPaused = false;
        mRecordingStartTime = SystemClock.uptimeMillis();

        mRecordingTimeText.setCompoundDrawablesWithIntrinsicBounds(
                R.drawable.ic_recording_indicator, 0, 0, 0);
        if (mPauseListener != null) {
            mPauseListener.onButtonContinue();
        }
        updateRecordingTime();
    }

    public void reset() {
        mRecordingTimeText.setCompoundDrawablesWithIntrinsicBounds(
                R.drawable.ic_recording_indicator, 0, 0, 0);
        mStarted = false;
        mPauseButton.setPaused(false);
        mPaused = false;
        mRecordingTimeText.setText("");
    }

    public void start() {
        start(0, 0, 0);
    }

    public void start(int frameRate, long frameInterval, long durationMs) {
        reset();
        mFrameRate = frameRate;
        mInterval = frameInterval;
        mDurationMs = durationMs;
        mRecordingTotalTime = 0L;
        mRecordingStartTime = SystemClock.uptimeMillis();
        mStarted = true;
        animate().alpha(1.0f).withStartAction(new Runnable() {
            @Override
            public void run() {
                updateRecordingTime();
                setVisibility(View.VISIBLE);
            }
        });

        Log.d(TAG, "started: frameRate=" + frameRate + " frameInterval=" + frameInterval + " maxDuration=" + durationMs);
    }

    public void stop() {
        mStarted = false;
        animate().alpha(0.0f).withEndAction(new Runnable() {
            @Override
            public void run() {
                setVisibility(View.GONE);
            }
        });
    }

    public void showTimeLapse(boolean show) {
        mTimeLapse = show;
        mTimeLapseLabel.setVisibility(show ? View.VISIBLE : View.GONE);
    }

    public long getTime() {
        return mPaused ? mRecordingTotalTime :
                SystemClock.uptimeMillis() - mRecordingStartTime + mRecordingTotalTime;
    }

    public void setPauseListener(PauseButton.OnPauseButtonListener listener) {
        mPauseListener = listener;
    }

    private void updateRecordingTime() {
        if (!mStarted || mPaused) {
            return;
        }

        long now = SystemClock.uptimeMillis();
        long delta = now - mRecordingStartTime + mRecordingTotalTime;

        // Starting a minute before reaching the max duration
        // limit, we'll countdown the remaining time instead.
        boolean countdownRemainingTime = (mDurationMs != 0
                && delta >= mDurationMs - 60000);

        long deltaAdjusted = delta;
        if (countdownRemainingTime) {
            deltaAdjusted = Math.max(0, mDurationMs - deltaAdjusted) + 999;
        }
        String text;

        long targetNextUpdateDelay;
        if (mInterval <= 0) {
            text = millisecondToTimeString(deltaAdjusted, false);
            targetNextUpdateDelay = 1000;
        } else {
            // The length of time lapse video is different from the length
            // of the actual wall clock time elapsed. Display the video length
            // only in format hh:mm:ss.dd, where dd are the centi seconds.
            text = millisecondToTimeString(getTimeLapseVideoLength(delta), true);
            targetNextUpdateDelay = mInterval;
        }

        mRecordingTimeText.setText(text);

        if (mRecordingTimeCountsDown != countdownRemainingTime) {
            // Avoid setting the color on every update, do it only
            // when it needs changing.
            mRecordingTimeCountsDown = countdownRemainingTime;

            int color = getContext().getResources().getColor(countdownRemainingTime
                    ? R.color.recording_time_remaining_text
                    : R.color.recording_time_elapsed_text);

            mRecordingTimeText.setTextColor(color);
        }

        long actualNextUpdateDelay = targetNextUpdateDelay - (delta % targetNextUpdateDelay);
        mHandler.sendEmptyMessageDelayed(
                UPDATE_RECORD_TIME, actualNextUpdateDelay);
    }

    private long getTimeLapseVideoLength(long deltaMs) {
        // For better approximation calculate fractional number of frames captured.
        // This will update the video time at a higher resolution.
        double numberOfFrames = (double) deltaMs / mInterval;
        return (long) (numberOfFrames / mFrameRate * 1000);
    }

    private static String millisecondToTimeString(long milliSeconds, boolean displayCentiSeconds) {
        long seconds = milliSeconds / 1000; // round down to compute seconds
        long minutes = seconds / 60;
        long hours = minutes / 60;
        long remainderMinutes = minutes - (hours * 60);
        long remainderSeconds = seconds - (minutes * 60);

        StringBuilder timeStringBuilder = new StringBuilder();

        // Hours
        if (hours > 0) {
            if (hours < 10) {
                timeStringBuilder.append('0');
            }
            timeStringBuilder.append(hours);

            timeStringBuilder.append(':');
        }

        // Minutes
        if (remainderMinutes < 10) {
            timeStringBuilder.append('0');
        }
        timeStringBuilder.append(remainderMinutes);
        timeStringBuilder.append(':');

        // Seconds
        if (remainderSeconds < 10) {
            timeStringBuilder.append('0');
        }
        timeStringBuilder.append(remainderSeconds);

        // Centi seconds
        if (displayCentiSeconds) {
            timeStringBuilder.append('.');
            long remainderCentiSeconds = (milliSeconds - seconds * 1000) / 10;
            if (remainderCentiSeconds < 10) {
                timeStringBuilder.append('0');
            }
            timeStringBuilder.append(remainderCentiSeconds);
        }

        return timeStringBuilder.toString();
    }
}
