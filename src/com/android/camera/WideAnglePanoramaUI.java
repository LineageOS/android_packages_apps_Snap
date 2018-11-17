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

import java.lang.reflect.Method;

import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.SurfaceTexture;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.util.Log;
import android.view.Gravity;
import android.view.Display;
import android.view.LayoutInflater;
import android.view.TextureView;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.android.camera.ui.CameraControls;
import com.android.camera.ui.CameraRootView;
import com.android.camera.ui.ModuleSwitcher;
import com.android.camera.ui.RotateImageView;
import com.android.camera.ui.RotateLayout;
import com.android.camera.ui.RotateTextToast;
import com.android.camera.util.CameraUtil;
import org.codeaurora.snapcam.R;

/**
 * The UI of {@link WideAnglePanoramaModule}.
 */
public class WideAnglePanoramaUI implements
        TextureView.SurfaceTextureListener,
        ShutterButton.OnShutterButtonListener,
        CameraRootView.MyDisplayListener,
        View.OnLayoutChangeListener {

    @SuppressWarnings("unused")
    private static final String TAG = "CAM_WidePanoramaUI";

    private CameraActivity mActivity;
    private WideAnglePanoramaController mController;

    private ViewGroup mRootView;
    private ModuleSwitcher mSwitcher;
    private FrameLayout mCaptureLayout;
    private View mReviewLayout;
    private ImageView mReview;
    private View mPreviewBorder;
    private View mLeftIndicator;
    private View mRightIndicator;
    private View mCaptureIndicator;
    private PanoProgressBar mCaptureProgressBar;
    private PanoProgressBar mSavingProgressBar;
    private TextView mTooFastPrompt;
    private View mPreviewLayout;
    private ViewGroup mReviewControl;
    private TextureView mTextureView;
    private ShutterButton mShutterButton;
    private CameraControls mCameraControls;
    private ImageView mThumbnail;
    private Bitmap mThumbnailBitmap;

    private Matrix mProgressDirectionMatrix = new Matrix();
    private float[] mProgressAngle = new float[2];

    private DialogHelper mDialogHelper;

    // Color definitions.
    private int mIndicatorColor;
    private int mIndicatorColorFast;
    private SurfaceTexture mSurfaceTexture;
    private View mPreviewCover;

    private int mOrientation;
    private int mPreviewYOffset;
    private RotateLayout mWaitingDialog;
    private RotateLayout mPanoFailedDialog;
    private Button mPanoFailedButton;

    private int mTopMargin = 0;
    private int mBottomMargin = 0;
    private float mAspectRatio = 1.0f;

    /** Constructor. */
    public WideAnglePanoramaUI(
            CameraActivity activity,
            WideAnglePanoramaController controller,
            ViewGroup root) {
        mActivity = activity;
        mController = controller;
        mRootView = root;

        createContentView();
        mSwitcher = (ModuleSwitcher) mRootView.findViewById(R.id.camera_switcher);
        mSwitcher.setCurrentIndex(ModuleSwitcher.WIDE_ANGLE_PANO_MODULE_INDEX);
        mSwitcher.setSwitchListener(mActivity);
        if (!mActivity.isSecureCamera()) {
            mThumbnail = (ImageView) mRootView.findViewById(R.id.preview_thumb);
            mThumbnail.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (!CameraControls.isAnimating())
                        mActivity.gotoGallery();
                }
            });
        }

        mSwitcher.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mSwitcher.showPopup();
                mSwitcher.setOrientation(mOrientation, false);
            }
        });

        RotateImageView muteButton = (RotateImageView)mRootView.findViewById(R.id.mute_button);
        muteButton.setVisibility(View.GONE);
    }

    private void calculateMargins(Point size) {
        int l = size.x > size.y ? size.x : size.y;
        int tm = mActivity.getResources().getDimensionPixelSize(R.dimen.preview_top_margin);
        int bm = mActivity.getResources().getDimensionPixelSize(R.dimen.preview_bottom_margin);
        mTopMargin = l / 4 * tm / (tm + bm);
        mBottomMargin = l / 4 - mTopMargin;
    }

    public void onStartCapture() {
        hideSwitcher();
        mShutterButton.setImageResource(R.drawable.shutter_button_stop);
        mCaptureIndicator.setVisibility(View.VISIBLE);
        showDirectionIndicators(PanoProgressBar.DIRECTION_NONE);
    }

    public void showPreviewUI() {
        mCaptureLayout.setVisibility(View.VISIBLE);
        showUI();
    }

    public void onStopCapture() {
        mCaptureIndicator.setVisibility(View.INVISIBLE);
        hideTooFastIndication();
        hideDirectionIndicators();
    }

    public void hideSwitcher() {
        mSwitcher.closePopup();
        mSwitcher.setVisibility(View.INVISIBLE);
    }

    public void hideUI() {
        hideSwitcher();
        mCameraControls.setVisibility(View.INVISIBLE);
    }

    public void showUI() {
        showSwitcher();
        mCameraControls.setVisibility(View.VISIBLE);
    }

    public void onPreviewFocusChanged(boolean previewFocused) {
        if (previewFocused) {
            showUI();
        } else {
            hideUI();
        }
    }

    public boolean arePreviewControlsVisible() {
        return (mCameraControls.getVisibility() == View.VISIBLE);
    }

    public void showSwitcher() {
        mSwitcher.setVisibility(View.VISIBLE);
    }

    public void setSwitcherIndex() {
        mSwitcher.setCurrentIndex(ModuleSwitcher.WIDE_ANGLE_PANO_MODULE_INDEX);
    }

    public void setCaptureProgressOnDirectionChangeListener(
            PanoProgressBar.OnDirectionChangeListener listener) {
        mCaptureProgressBar.setOnDirectionChangeListener(listener);
    }

    public void resetCaptureProgress() {
        mCaptureProgressBar.reset();
    }

    public void setMaxCaptureProgress(int max) {
        mCaptureProgressBar.setMaxProgress(max);
    }

    public void showCaptureProgress() {
        mCaptureProgressBar.setVisibility(View.VISIBLE);
    }

    public void updateCaptureProgress(
            float panningRateXInDegree, float panningRateYInDegree,
            float progressHorizontalAngle, float progressVerticalAngle,
            float maxPanningSpeed) {

        if ((Math.abs(panningRateXInDegree) > maxPanningSpeed)
                || (Math.abs(panningRateYInDegree) > maxPanningSpeed)) {
            showTooFastIndication();
        } else {
            hideTooFastIndication();
        }

        // progressHorizontalAngle and progressVerticalAngle are relative to the
        // camera. Convert them to UI direction.
        mProgressAngle[0] = progressHorizontalAngle;
        mProgressAngle[1] = progressVerticalAngle;
        mProgressDirectionMatrix.mapPoints(mProgressAngle);

        int angleInMajorDirection =
                (Math.abs(mProgressAngle[0]) > Math.abs(mProgressAngle[1]))
                        ? (int) mProgressAngle[0]
                        : (int) mProgressAngle[1];
        mCaptureProgressBar.setProgress((angleInMajorDirection));
    }

    public void setProgressOrientation(int orientation) {
        mProgressDirectionMatrix.reset();
        mProgressDirectionMatrix.postRotate(orientation);
    }

    public void showDirectionIndicators(int direction) {
        switch (direction) {
            case PanoProgressBar.DIRECTION_NONE:
                mLeftIndicator.setVisibility(View.VISIBLE);
                mRightIndicator.setVisibility(View.VISIBLE);
                break;
            case PanoProgressBar.DIRECTION_LEFT:
                mLeftIndicator.setVisibility(View.VISIBLE);
                mRightIndicator.setVisibility(View.INVISIBLE);
                break;
            case PanoProgressBar.DIRECTION_RIGHT:
                mLeftIndicator.setVisibility(View.INVISIBLE);
                mRightIndicator.setVisibility(View.VISIBLE);
                break;
        }
    }

    public SurfaceTexture getSurfaceTexture() {
        return mSurfaceTexture;
    }

    @Override
    public void onSurfaceTextureAvailable(SurfaceTexture surfaceTexture, int i, int i2) {
        mSurfaceTexture = surfaceTexture;
        mController.onPreviewUIReady();
        mActivity.updateThumbnail(mThumbnail);
    }

    @Override
    public void onSurfaceTextureSizeChanged(SurfaceTexture surfaceTexture, int i, int i2) {

    }

    @Override
    public boolean onSurfaceTextureDestroyed(SurfaceTexture surfaceTexture) {
        mController.onPreviewUIDestroyed();
        mSurfaceTexture = null;
        Log.d(TAG, "surfaceTexture is destroyed");
        return true;
    }

    @Override
    public void onSurfaceTextureUpdated(SurfaceTexture surfaceTexture) {
        // Make sure preview cover is hidden if preview data is available.
        if (mPreviewCover.getVisibility() != View.GONE) {
            mPreviewCover.setVisibility(View.GONE);
        }
    }

    private void hideDirectionIndicators() {
        mLeftIndicator.setVisibility(View.INVISIBLE);
        mRightIndicator.setVisibility(View.INVISIBLE);
    }

    public Point getPreviewAreaSize() {
        return new Point(
                mTextureView.getWidth(), mTextureView.getHeight());
    }

    public void reset() {
        mShutterButton.setImageResource(R.drawable.btn_new_shutter_panorama);
        mReviewLayout.setVisibility(View.GONE);
        mCaptureProgressBar.setVisibility(View.INVISIBLE);
    }

    public void saveFinalMosaic(Bitmap bitmap, int orientation) {
        if (bitmap != null && orientation != 0) {
            Matrix rotateMatrix = new Matrix();
            rotateMatrix.setRotate(orientation);
            bitmap = Bitmap.createBitmap(
                    bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(),
                    rotateMatrix, false);
        }

        mReview.setImageBitmap(bitmap);
        mCaptureLayout.setVisibility(View.GONE);
        mReviewLayout.setVisibility(View.VISIBLE);
        // If capture is stopped by device rotation, the rendering progress bar
        // is sometimes not shown due to wrong layout result. It's likely to be
        // a framework bug. Call requestLayout() as a workaround.
        mSavingProgressBar.requestLayout();

        mThumbnailBitmap = bitmap;
    }

    public void showFinalMosaic() {
        if (mThumbnailBitmap == null) return;
        mActivity.updateThumbnail(mThumbnailBitmap);
        mThumbnailBitmap.recycle();
        mThumbnailBitmap = null;
    }

    public void onConfigurationChanged(
            Configuration newConfig, boolean threadRunning) {
        Drawable lowResReview = null;
        if (threadRunning) lowResReview = mReview.getDrawable();

        // Change layout in response to configuration change
        LayoutInflater inflater = (LayoutInflater)
                mActivity.getSystemService(Context.LAYOUT_INFLATER_SERVICE);

        mReviewControl.removeAllViews();
        ((ViewGroup) mReviewControl).clearDisappearingChildren();
        inflater.inflate(R.layout.pano_review_control, mReviewControl, true);

        mRootView.bringChildToFront(mCameraControls);
        setViews(mActivity.getResources());
        if (threadRunning) {
            mReview.setImageDrawable(lowResReview);
            mCaptureLayout.setVisibility(View.GONE);
            mReviewLayout.setVisibility(View.VISIBLE);
        }
    }

    private void setPanoramaPreviewView() {
        int rotation = mActivity.getWindowManager().getDefaultDisplay().getRotation();
        Display display = mActivity.getWindowManager().getDefaultDisplay();
        Point size = new Point();
        display.getSize(size);
        mActivity.getWindowManager().getDefaultDisplay().getRealSize(size);
        calculateMargins(size);
        mCameraControls.setMargins(mTopMargin, mBottomMargin);

        int width = size.x;
        int height = size.y - mTopMargin - mBottomMargin;
        int xOffset = 0;
        int yOffset = 0;
        int w = width;
        int h = height;

        h = w * 4 / 3;
        yOffset = (height - h) / 2 + mTopMargin;

        FrameLayout.LayoutParams param = new FrameLayout.LayoutParams(w, h);
        mTextureView.setLayoutParams(param);
        mTextureView.setX(xOffset);
        mTextureView.setY(yOffset);
        mPreviewYOffset = yOffset;

        mTextureView.layout(0, mPreviewYOffset, mTextureView.getRight(), mTextureView.getBottom());
    }

    public void resetSavingProgress() {
        mSavingProgressBar.reset();
        mSavingProgressBar.setRightIncreasing(true);
    }

    public void updateSavingProgress(int progress) {
        mSavingProgressBar.setProgress(progress);
    }

    @Override
    public void onShutterButtonFocus(boolean pressed) {
        // Do nothing.
    }

    @Override
    public void onShutterButtonClick() {
        mController.onShutterButtonClick();
    }

    @Override
    public void onShutterButtonLongClick() {}

    @Override
    public void onLayoutChange(
            View v, int l, int t, int r, int b,
            int oldl, int oldt, int oldr, int oldb) {
        mController.onPreviewUILayoutChange(l, t, r, b);
    }

    public void showAlertDialog(
            String title, String failedString,
            String OKString, Runnable runnable) {
        mDialogHelper.showAlertDialog(title, failedString, OKString, runnable);
    }

    public void showWaitingDialog(String title) {
        mDialogHelper.showWaitingDialog(title);
    }

    public void dismissAllDialogs() {
        mDialogHelper.dismissAll();
    }

    private void createContentView() {
        LayoutInflater inflator = (LayoutInflater) mActivity
                .getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        inflator.inflate(R.layout.panorama_module, mRootView, true);

        Resources appRes = mActivity.getResources();
        mIndicatorColor = appRes.getColor(R.color.pano_progress_indication);
        mIndicatorColorFast = appRes.getColor(R.color.pano_progress_indication_fast);

        mPreviewCover = mRootView.findViewById(R.id.preview_cover);
        mPreviewLayout = mRootView.findViewById(R.id.pano_preview_layout);
        mReviewControl = (ViewGroup) mRootView.findViewById(R.id.pano_review_control);
        mReviewLayout = mRootView.findViewById(R.id.pano_review_layout);
        mReview = (ImageView) mRootView.findViewById(R.id.pano_reviewarea);
        mCaptureLayout = (FrameLayout) mRootView.findViewById(R.id.panorama_capture_layout);
        mCaptureProgressBar = (PanoProgressBar) mRootView.findViewById(R.id.pano_pan_progress_bar);
        mCaptureProgressBar.setBackgroundColor(appRes.getColor(R.color.pano_progress_empty));
        mCaptureProgressBar.setDoneColor(appRes.getColor(R.color.pano_progress_done));
        mCaptureProgressBar.setIndicatorColor(mIndicatorColor);
        mCaptureProgressBar.setIndicatorWidth(20);

        mPreviewBorder = mCaptureLayout.findViewById(R.id.pano_preview_area_border);

        mLeftIndicator = mRootView.findViewById(R.id.pano_pan_left_indicator);
        mRightIndicator = mRootView.findViewById(R.id.pano_pan_right_indicator);
        mLeftIndicator.setEnabled(false);
        mRightIndicator.setEnabled(false);
        mTooFastPrompt = (TextView) mRootView.findViewById(R.id.pano_capture_too_fast_textview);
        mCaptureIndicator = mRootView.findViewById(R.id.pano_capture_indicator);

        mShutterButton = (ShutterButton) mRootView.findViewById(R.id.shutter_button);
        mShutterButton.setImageResource(R.drawable.btn_new_shutter);
        mShutterButton.setOnShutterButtonListener(this);
        // Hide menu
        mRootView.findViewById(R.id.menu).setVisibility(View.GONE);

        // TODO: set display change listener properly.
        ((CameraRootView) mRootView).setDisplayChangeListener(null);
        mTextureView = (TextureView) mRootView.findViewById(R.id.pano_preview_textureview);
        mTextureView.setSurfaceTextureListener(this);
        mTextureView.addOnLayoutChangeListener(this);
        mCameraControls = (CameraControls) mRootView.findViewById(R.id.camera_controls);
        setPanoramaPreviewView();

        mWaitingDialog = (RotateLayout) mRootView.findViewById(R.id.waitingDialog);
        mPanoFailedDialog = (RotateLayout) mRootView.findViewById(R.id.pano_dialog_layout);
        mPanoFailedButton = (Button) mRootView.findViewById(R.id.pano_dialog_button1);
        mDialogHelper = new DialogHelper();
        setViews(appRes);
    }

    private void setViews(Resources appRes) {
        int weight = appRes.getInteger(R.integer.SRI_pano_layout_weight);

        mSavingProgressBar = (PanoProgressBar) mRootView.findViewById(R.id.pano_saving_progress_bar);
        mSavingProgressBar.setIndicatorWidth(0);
        mSavingProgressBar.setMaxProgress(100);
        mSavingProgressBar.setBackgroundColor(appRes.getColor(R.color.pano_progress_empty));
        mSavingProgressBar.setDoneColor(appRes.getColor(R.color.pano_progress_indication));

        View cancelButton = mRootView.findViewById(R.id.pano_review_cancel_button);
        cancelButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View arg0) {
                mController.cancelHighResStitching();
            }
        });


    }

    private void showTooFastIndication() {
        mTooFastPrompt.setVisibility(View.VISIBLE);
        // The PreviewArea also contains the border for "too fast" indication.
        mPreviewBorder.setVisibility(View.VISIBLE);
        mCaptureProgressBar.setIndicatorColor(mIndicatorColorFast);
        mLeftIndicator.setEnabled(true);
        mRightIndicator.setEnabled(true);
    }

    private void hideTooFastIndication() {
        mTooFastPrompt.setVisibility(View.GONE);
        mPreviewBorder.setVisibility(View.INVISIBLE);
        mCaptureProgressBar.setIndicatorColor(mIndicatorColor);
        mLeftIndicator.setEnabled(false);
        mRightIndicator.setEnabled(false);
    }

    public void flipPreviewIfNeeded() {
        // Rotation needed to display image correctly clockwise
        int cameraOrientation = mController.getCameraOrientation();
        // Display rotated counter-clockwise
        int displayRotation = CameraUtil.getDisplayRotation(mActivity);
        // Rotation needed to display image correctly on current display
        int rotation = (cameraOrientation - displayRotation + 360) % 360;
        if (rotation >= 180) {
            mTextureView.setRotation(180);
        } else {
            mTextureView.setRotation(0);
        }
    }

    @Override
    public void onDisplayChanged() {
        mCameraControls.checkLayoutFlip();
        flipPreviewIfNeeded();
    }

    public void initDisplayChangeListener() {
        ((CameraRootView) mRootView).setDisplayChangeListener(this);
    }

    public void removeDisplayChangeListener() {
        ((CameraRootView) mRootView).removeDisplayChangeListener();
    }

    public void showPreviewCover() {
        mPreviewCover.setVisibility(View.VISIBLE);
    }

    private class DialogHelper {

        DialogHelper() {
        }

        public void dismissAll() {
            if (mPanoFailedDialog != null) {
                mPanoFailedDialog.setVisibility(View.INVISIBLE);
            }
            if (mWaitingDialog != null) {
                mWaitingDialog.setVisibility(View.INVISIBLE);
            }
        }

        public void showAlertDialog(
                CharSequence title, CharSequence message,
                CharSequence buttonMessage, final Runnable buttonRunnable) {
            dismissAll();
            mPanoFailedButton.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    buttonRunnable.run();
                    mPanoFailedDialog.setVisibility(View.INVISIBLE);
                }
            });
            mPanoFailedDialog.setVisibility(View.VISIBLE);
        }

        public void showWaitingDialog(CharSequence message) {
            dismissAll();
            mWaitingDialog.setVisibility(View.VISIBLE);
        }
    }

    private static class FlipBitmapDrawable extends BitmapDrawable {

        public FlipBitmapDrawable(Resources res, Bitmap bitmap) {
            super(res, bitmap);
        }

        @Override
        public void draw(Canvas canvas) {
            Rect bounds = getBounds();
            int cx = bounds.centerX();
            int cy = bounds.centerY();
            canvas.save(Canvas.MATRIX_SAVE_FLAG);
            canvas.rotate(180, cx, cy);
            super.draw(canvas);
            canvas.restore();
        }
    }

    public boolean hideSwitcherPopup() {
        if (mSwitcher != null && mSwitcher.showsPopup()) {
            mSwitcher.closePopup();
            return true;
        }
        return false;
   }

    public void setOrientation(int orientation, boolean animation) {
        mOrientation = orientation;
        // '---------`
        // |    0    |
        // |---------| =t
        // | |     | |
        // |1|     |2|
        // | |     | |
        // |---------| =b1
        // |    3    |
        // `---------' =b2
        //          =r
        final View dummy = mRootView.findViewById(R.id.pano_dummy_layout);
        int t = dummy.getTop();
        int b1 = dummy.getBottom();
        int r = dummy.getRight();
        int b2 = dummy.getBottom();
        final FrameLayout progressLayout = (FrameLayout)
                mRootView.findViewById(R.id.pano_progress_layout);
        int pivotY = ((ViewGroup) progressLayout).getPaddingTop()
                + progressLayout.getChildAt(0).getHeight() / 2;

        int[] x = { r / 2, r / 10, r * 9 / 10, r / 2 };
        int[] y = { t / 2 + pivotY, (t + b1) / 2, (t + b1) / 2, b1 + pivotY };

        int idx1, idx2;
        int g;
        switch (orientation) {
            case 90:
                idx1 = 1;
                idx2 = 2;
                g = Gravity.TOP | Gravity.RIGHT;
                break;
            case 180:
                idx1 = 3;
                idx2 = 0;
                g = Gravity.CENTER_HORIZONTAL | Gravity.BOTTOM;
                break;
            case 270:
                idx1 = 2;
                idx2 = 1;
                g = Gravity.TOP | Gravity.RIGHT;
                break;
            default:
                idx1 = 0;
                idx2 = 3;
                g = Gravity.CENTER_HORIZONTAL | Gravity.BOTTOM;
                break;
        }

        final View[] views1 = {
            (View) mCaptureIndicator.getParent(),
            mRootView.findViewById(R.id.pano_review_indicator)
        };
        for (final View v : views1) {
            v.setTranslationX(x[idx1] - x[0]);
            v.setTranslationY(y[idx1]- y[0]);
            // use relection here to build on Kitkat
            if (Build.VERSION.SDK_INT >= 21) {
                try {
                    final Class cls = Class.forName("android.view.View");
                    final Method method = cls.getMethod("setTranslationZ", float.class);
                    method.invoke(v, 1);
                } catch (Exception e) {
                    // ignore
                }
            }
            v.setRotation(-orientation);
        }

        final View[] views2 = { progressLayout, mReviewControl };
        for (final View v : views2) {
            v.setPivotX(r / 2);
            v.setPivotY(pivotY);
            v.setTranslationX(x[idx2] - x[3]);
            v.setTranslationY(y[idx2] - y[3]);
            v.setRotation(-orientation);
        }

        final View button = mReviewControl.findViewById(R.id.pano_review_cancel_button);
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) button.getLayoutParams();
        lp.gravity = g;
        button.setLayoutParams(lp);
        mWaitingDialog.setRotation(-orientation);
        mPanoFailedDialog.setRotation(-orientation);
        mReview.setRotation(-orientation);
        mTooFastPrompt.setRotation(-orientation);
        mPreviewBorder.setRotation(-orientation);
        mCameraControls.setOrientation(orientation, animation);
        RotateTextToast.setOrientation(orientation);
    }

    public void hidePreviewCover() {
        // Hide the preview cover if need.
        if (mPreviewCover.getVisibility() != View.GONE) {
            mPreviewCover.setVisibility(View.GONE);
        }
    }

    public void setPreviewSize(int width, int height) {
        if (width == 0 || height == 0) {
            Log.w(TAG, "Preview size should not be 0.");
            return;
        }
        float ratio;
        if (width > height) {
            ratio = (float) width / height;
        } else {
            ratio = (float) height / width;
        }

        if (ratio != mAspectRatio) {
            mAspectRatio = ratio;
        }

        mCameraControls.setPreviewRatio(mAspectRatio, false);
    }
}
