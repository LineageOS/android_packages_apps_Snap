/*
 * Copyright (C) 2019-2020 The LineageOS Project
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
package org.lineageos.quickreader;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.Log;
import android.util.Patterns;
import android.view.Window;
import android.view.WindowManager;
import android.webkit.URLUtil;
import android.widget.FrameLayout;
import android.widget.ImageView;

import com.google.zxing.Result;

import java.util.concurrent.ExecutionException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.android.camera.CameraActivity;
import me.dm7.barcodescanner.zxing.ZXingScannerView;

import org.codeaurora.snapcam.R;

public class ScannerActivity extends Activity implements ZXingScannerView.ResultHandler {
    private static final String TAG = "QuickReader";
    private static final int REQUEST_CAMERA = 391;
    public static final Pattern ACCEPTED_URI_SCHEMA = Pattern.compile(
            "(?i)" + // switch on case insensitive matching
                    "(" +    // begin group for schema
                    "(?:http|https|file|chrome)://" +
                    "|(?:inline|data|about|javascript):" +
                    ")" +
                    "(.*)"
    );

    public static final String SECURE_CAMERA_EXTRA = "secure_camera";

    private static ScannerIntentHelper sHelper;

    private AnalyzeTask task;

    private ZXingScannerView mScanView;
    private FrameLayout mIdentifyLayout;
    private ImageView mIdentifyIcon;
    private ImageView mFlashIcon;

    private boolean allowReading = true;

    @Override
    protected void onCreate(Bundle savedInstance) {
        super.onCreate(savedInstance);

        Intent intent = getIntent();
        boolean isSecure = intent.getBooleanExtra(SECURE_CAMERA_EXTRA, false);
        if (isSecure) {
            // Change the window flags so that secure camera can show when locked
            Window win = getWindow();
            WindowManager.LayoutParams params = win.getAttributes();
            params.flags |= WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED;
            win.setAttributes(params);
        }

        setContentView(R.layout.activity_scanner);

        mScanView = (ZXingScannerView) findViewById(R.id.scanner_view);
        mIdentifyLayout = (FrameLayout) findViewById(R.id.identify_layout);
        mIdentifyIcon = (ImageView) findViewById(R.id.identify_icon);
        mFlashIcon = (ImageView) findViewById(R.id.action_flash);
        ImageView closeIcon = (ImageView) findViewById(R.id.action_close);

        mIdentifyLayout.setOnClickListener(v -> {
                isSecure ? showClickErrorDialog() : sHelper.run(this);
            }
        });
        mFlashIcon.setOnClickListener(v -> toggleFlash());
        closeIcon.setOnClickListener(v -> finish());

        if (!hasCameraPermission()) {
            requestPermission();
        }

        sHelper = ScannerIntentHelper.getInstance();
        task = new AnalyzeTask();
    }

    @Override
    public void onResume() {
        super.onResume();

        mScanView.setResultHandler(this);
        mScanView.startCamera();
    }

    @Override
    public void onPause() {
        super.onPause();

        try {
            if (mScanView.getFlash()) {
                mScanView.setFlash(false);
                mFlashIcon.setImageResource(R.drawable.ic_flash_off);
            }
        } catch (Exception ignored) {
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        mScanView.stopCamera();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions,
            int[] grantResults) {
        if (requestCode != REQUEST_CAMERA || permissions.length == 0 || grantResults.length == 0) {
            return;
        }

        if (grantResults[0] != PackageManager.PERMISSION_GRANTED) {
            if (shouldShowRequestPermissionRationale(Manifest.permission.CAMERA)) {
                showRationaleErrorDialog();
            } else {
                showFatalErrorDialog();
            }
        }
    }

    @Override
    public void handleResult(Result rawResult) {
        mScanView.resumeCameraPreview(this);

        if (!allowReading) {
            return;
        }

        if (task.getStatus() == AsyncTask.Status.RUNNING) {
            return;
        }

        allowReading = false;
        task = new AnalyzeTask();
        task.execute(rawResult.getText());

        try {
            int result = task.get();
            postAnalyze(result);
            allowReading = true;
        } catch (InterruptedException | ExecutionException e) {
            Log.e(TAG, e.getMessage());
        }
    }

    private boolean hasCameraPermission() {
        return checkSelfPermission(Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED;
    }

    private void requestPermission() {
        requestPermissions(new String[] { Manifest.permission.CAMERA }, REQUEST_CAMERA);
    }

    private void openSettings() {
        Uri uri = Uri.fromParts("package", getPackageName(), null);
        Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, uri);
        startActivity(intent);
    }

    private void showRationaleErrorDialog() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.quick_reader_permission_error_title)
                .setMessage(R.string.quick_reader_permission_rationale_message)
                .setPositiveButton(R.string.quick_reader_permission_rationale_positive,
                        (dialog, i) -> requestPermission())
                .setNegativeButton(R.string.quick_reader_action_dismiss,
                        (dialog, i) -> finish())
                .show();
    }

    private void showFatalErrorDialog() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.quick_reader_permission_error_title)
                .setMessage(R.string.quick_reader_permission_fatal_message)
                .setPositiveButton(R.string.quick_reader_permission_fatal_positive,
                        (dialog, i) -> openSettings())
                .setNegativeButton(R.string.quick_reader_action_dismiss,
                        (dialog, i) -> finish())
                .show();
    }

    private void showClickErrorDialog() {
        new AlertDialog.Builder(this)
                .setMessage(R.string.quick_reader_only_unlocked)
                .setPositiveButton(R.string.quick_reader_action_dismiss, null)
                .show();
    }

    private void postAnalyze(int result) {
        if (result == 0 || !sHelper.isValid()) {
            return;
        }

        showScanResult(result);
    }

    private void showScanResult(int imageResource) {
        mIdentifyIcon.setImageResource(imageResource);

        if (mIdentifyLayout.getScaleX() == 0) {
            // First shown, animate
            mIdentifyLayout.animate()
                    .scaleX(1)
                    .scaleY(1)
                    .start();
        }
    }

    private void toggleFlash() {
        boolean isEnabled = mScanView.getFlash();
        mScanView.setFlash(!isEnabled);
        mFlashIcon.setImageResource(isEnabled ? R.drawable.ic_flash_off : R.drawable.ic_flash_on);
    }

    private static class AnalyzeTask extends AsyncTask<String, Void, Integer> {
        private static final String ID_SMS = "smsto:";
        private static final String ID_EMAIL = "mailto:";
        private static final String ID_PHONE = "tel:";
        private static final String ID_LOCATION = "geo:";
        private static final String ID_VCARD = "BEGIN:VCARD";
        private static final String ID_VEVENT = "BEGIN:VEVENT";
        private static final String ID_MECARD = "MECARD:";
        private static final String ID_MEBKM_URL = "MEBKM:TITLE:";

        @Override
        protected Integer doInBackground(String... params) {
            if (params.length == 0) {
                return null;
            }

            String content = params[0];
            int icon;

            if (TextUtils.isEmpty(content)) {
                return null;
            }

            sHelper.reset();

            if (content.startsWith(ID_SMS)) {
                icon = R.drawable.ic_sms;
                sHelper.setSMSIntent(content);
            } else if (content.startsWith(ID_EMAIL)) {
                icon = R.drawable.ic_email;
            } else if (content.startsWith(ID_PHONE)) {
                icon = R.drawable.ic_phone;
                sHelper.setUriIntent(content);
            } else if (content.startsWith(ID_LOCATION)) {
                sHelper.setUriIntent(content);
                icon = R.drawable.ic_location;
            } else if (content.startsWith(ID_VEVENT)) {
                sHelper.setCalendarIntent(content);
                icon = R.drawable.ic_event;
            } else if (content.startsWith(ID_MECARD)) {
                sHelper.setContactIntent(content.replace(ID_MECARD, ""), false);
                icon = R.drawable.ic_contact;
            } else if (content.startsWith(ID_VCARD)) {
                sHelper.setContactIntent(content, true);
                icon = R.drawable.ic_contact;
            } else if (content.startsWith(ID_MEBKM_URL)) {
                sHelper.setMeBkmUrl(content);
                icon = R.drawable.ic_http;
            } else {
                String properContent = smartUrlFilter(content);

                if (TextUtils.isEmpty(properContent)) {
                    // Fallback to plain text
                    sHelper.setText(content);
                    return R.drawable.ic_text;
                }

                sHelper.setUriIntent(properContent);
                icon = R.drawable.ic_http;
            }

            return icon;
        }

        /**
         * Attempts to determine whether user input is a URL or search
         * terms.  Anything with a space is passed to search if canBeSearch is true.
         *
         * Converts to lowercase any mistakenly uppercased schema (i.e.,
         * "Http://" converts to "http://"
         *
         * @return Original or modified URL
         *
         */
        private String smartUrlFilter(String url) {
            String inUrl = url.trim();
            boolean hasSpace = inUrl.indexOf(' ') != -1;

            Matcher matcher = ACCEPTED_URI_SCHEMA.matcher(inUrl);
            if (matcher.matches()) {
                // force scheme to lowercase
                String scheme = matcher.group(1);
                String lcScheme = scheme.toLowerCase();
                if (!lcScheme.equals(scheme)) {
                    inUrl = lcScheme + matcher.group(2);
                }
                if (hasSpace && Patterns.WEB_URL.matcher(inUrl).matches()) {
                    inUrl = inUrl.replace(" ", "%20");
                }
                return inUrl;
            }
            if (!hasSpace && Patterns.WEB_URL.matcher(inUrl).matches()) {
                return URLUtil.guessUrl(inUrl);
            }
            return null;
        }
    }
}