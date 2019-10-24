/*
 * Copyright (c) 2019-2020, The Linux Foundation. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are
 * met:
 *     * Redistributions of source code must retain the above copyright
 *       notice, this list of conditions and the following disclaimer.
 *     * Redistributions in binary form must reproduce the above
 *       copyright notice, this list of conditions and the following
 *       disclaimer in the documentation and/or other materials provided
 *       with the distribution.
 *     * Neither the name of The Linux Foundation nor the names of its
 *       contributors may be used to endorse or promote products derived
 *       from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED "AS IS" AND ANY EXPRESS OR IMPLIED
 * WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NON-INFRINGEMENT
 * ARE DISCLAIMED.  IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS
 * BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR
 * BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
 * WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE
 * OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN
 * IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

/*
Not a contribution.
*/

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


package com.android.camera.multi;

import android.app.ActionBar;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.ImageFormat;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.MediaRecorder;
import android.media.CamcorderProfile;
import android.os.Bundle;
import android.preference.ListPreference;
import android.preference.MultiSelectListPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceCategory;
import android.preference.PreferenceGroup;
import android.preference.PreferenceScreen;
import android.preference.SwitchPreference;
import android.view.Window;
import android.view.WindowManager;
import android.util.Log;
import android.util.Size;

import org.codeaurora.snapcam.R;
import com.android.camera.ComboPreferences;
import com.android.camera.CameraSettings;
import com.android.camera.SettingsManager;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Arrays;

public class MultiSettingsActivity extends PreferenceActivity {

    private static final String TAG = "MultiSettingsActivity";

    public static final String CAMERA_MODULE = "camera_module";

    // capture settings
    public static final String KEY_HAL_ZAL = "pref_multi_camera_hal_zsl_key";
    public static final String KEY_PICTURE_SIZE_1 = "pref_multi_camera_picturesize1_key";
    public static final String KEY_PICTURE_SIZE_2 = "pref_multi_camera_picturesize2_key";
    public static final String KEY_PICTURE_SIZE_3 = "pref_multi_camera_picturesize3_key";
    public static final String KEY_PICTURE_QUALITY = "pref_multi_camera_jpegquality_key";
    public static final String KEY_SHUTTER_SOUND = "pref_multi_camera_shutter_sound_key";

    // capture settings
    public static final String KEY_VIDEO_SIZE_1 = "pref_multi_camera_video_quality1_key";
    public static final String KEY_VIDEO_SIZE_2 = "pref_multi_camera_video_quality2_key";
    public static final String KEY_VIDEO_SIZE_3 = "pref_multi_camera_video_quality3_key";
    public static final String KEY_VIDEO_DURATION = "pref_multi_camera_video_duration_key";
    public static final String KEY_AUDIO_ENCODER = "pref_multi_camera_audioencoder_key";
    public static final String KEY_VIDEO_ROTATION = "pref_multi_camera_video_rotation_key";

    private static final String KEY_RESTORE_DEFAULT = "pref_multi_camera_restore_default";
    private static final String KEY_VERSION_INFO = "multi_camera_version_info";
    public static final String KEY_MULTI_CAMERAS_MODE = "pref_camera2_multi_cameras_key";
    public static final HashMap<Integer, String> KEY_PICTURE_SIZES = new HashMap<Integer, String>();
    public static final HashMap<Integer, String> KEY_VIDEO_SIZES = new HashMap<Integer, String>();

    private SharedPreferences mSharedPreferences;
    private SharedPreferences mLocalSharedPref;
    private SettingsManager mSettingsManager;
    private MultiCameraModule.CameraMode mMultiCameraMode;

    private ArrayList<CameraCharacteristics> mCharacteristics;

    static {
        KEY_PICTURE_SIZES.put(0, KEY_PICTURE_SIZE_1);
        KEY_PICTURE_SIZES.put(1, KEY_PICTURE_SIZE_2);
        KEY_PICTURE_SIZES.put(2, KEY_PICTURE_SIZE_3);

        KEY_VIDEO_SIZES.put(0, KEY_VIDEO_SIZE_1);
        KEY_VIDEO_SIZES.put(1, KEY_VIDEO_SIZE_2);
        KEY_VIDEO_SIZES.put(2, KEY_VIDEO_SIZE_3);
    }

    private SharedPreferences.OnSharedPreferenceChangeListener mSharedPreferenceChangeListener
            = new SharedPreferences.OnSharedPreferenceChangeListener() {
        @Override
        public void onSharedPreferenceChanged(SharedPreferences sharedPreferences,
                                              String key) {
            Preference p = findPreference(key);
            if (p == null) return;

            SharedPreferences.Editor editor = mLocalSharedPref.edit();
            String value;
            if (p instanceof SwitchPreference) {
                boolean checked = ((SwitchPreference) p).isChecked();
                value = checked ? "on" : "off";
                editor.putBoolean(key, checked);
            } else if (p instanceof ListPreference){
                value = ((ListPreference) p).getValue();
                editor.putString(key, value);
            } else if (p instanceof MultiSelectListPreference) {
                Set<String> valueSet = ((MultiSelectListPreference)p).getValues();
            }

            editor.apply();
            if (key.equals(KEY_MULTI_CAMERAS_MODE)) {
                String multiEnable = ((ListPreference) p).getValue();
                String multiCamerasOff = MultiSettingsActivity.this.getResources().getString(
                        R.string.pref_camera2_multi_cameras_value_off);
                if (multiEnable.equals(multiCamerasOff)) {
                    mSettingsManager.setValue(KEY_MULTI_CAMERAS_MODE, multiCamerasOff);
                }
            }
        }
    };

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        int flag = WindowManager.LayoutParams.FLAG_FULLSCREEN;
        Window window = getWindow();
        window.setFlags(flag, flag);
        ActionBar actionBar = getActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
            actionBar.setTitle(getResources().getString(R.string.settings_title));
        }

        mSettingsManager = SettingsManager.getInstance();
        if (mSettingsManager == null) {
            finish();
            return;
        }

        mMultiCameraMode = (MultiCameraModule.CameraMode) getIntent().getSerializableExtra(
                CAMERA_MODULE);

        initializeCameraCharacteristics();
        addPreferencesFromResource(R.xml.multi_setting_menu_preferences);
        filterPreferences();
        initializePreferences();
    }

    @Override
    protected void onStop() {
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mSharedPreferences.unregisterOnSharedPreferenceChangeListener(mSharedPreferenceChangeListener);
    }

    private void setShowInLockScreen() {
        // Change the window flags so that secure camera can show when locked
        Window win = getWindow();
        WindowManager.LayoutParams params = win.getAttributes();
        params.flags |= WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED;
        win.setAttributes(params);
    }

    private void initializeCameraCharacteristics() {
        mCharacteristics = new ArrayList<>();
        CameraManager manager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        try {
            String[] cameraIdList = manager.getCameraIdList();
            boolean isFirstBackCameraId = true;
            boolean isRearCameraPresent = false;
            Log.d(TAG, "cameraIdList size =" + cameraIdList.length);
            for (int i = 0; i < cameraIdList.length; i++) {
                String cameraId = cameraIdList[i];
                CameraCharacteristics characteristics
                        = manager.getCameraCharacteristics(cameraId);
                Log.d(TAG, " cameraId :" + cameraId + ", i :" + i);
                mCharacteristics.add(i, characteristics);
            }
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private void initializePreferences() {
        mSharedPreferences = getPreferenceManager().getSharedPreferences();
        mSharedPreferences.registerOnSharedPreferenceChangeListener(mSharedPreferenceChangeListener);

        mLocalSharedPref = this.getSharedPreferences(
                ComboPreferences.getLocalSharedPreferencesName(this,
                        "multi" + mMultiCameraMode), Context.MODE_PRIVATE);

        setOnPreferenceClick();

        // capture settings
        initializeHalZSLPref();
        initializePictureQuality();
        initializePictureSize1();
        initializePictureSize2();
        initializePictureSize3();
        initializeShutterSound();

        // video settins
        initializeVideoSize1();
        initializeVideoSize2();
        initializeVideoSize3();
        initializeVideoDuration();
        initializeAudioEncoder();
        initializeVideoRotation();

        initializeVersionInfo();
    }

    private void filterPreferences() {
        PreferenceGroup developer = (PreferenceGroup) findPreference("developer");
        PreferenceGroup photoPre = (PreferenceGroup) findPreference("photo");
        PreferenceGroup videoPre = (PreferenceGroup) findPreference("video");
        PreferenceScreen parentPre = getPreferenceScreen();

        switch (mMultiCameraMode) {
            case DEFAULT:
                removePreferenceGroup("video", parentPre);
                break;
            case VIDEO:
                removePreferenceGroup("photo", parentPre);
                break;
            default:
                //don't filter
                break;
        }
    }

    private void setOnPreferenceClick() {
        for (int i = 0; i < getPreferenceScreen().getPreferenceCount(); i++) {
            PreferenceCategory category = (PreferenceCategory) getPreferenceScreen().getPreference(i);
            for (int j = 0; j < category.getPreferenceCount(); j++) {
                Preference pref = category.getPreference(j);
                pref.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {

                    @Override
                    public boolean onPreferenceClick(Preference preference) {
                        if (preference.getKey().equals(KEY_RESTORE_DEFAULT)) {
                            onRestoreDefaultSettingsClick();
                        }
                        return false;
                    }

                });
            }
        }
    }

    private void initializeHalZSLPref() {
        boolean isCheck = false;
        if (mLocalSharedPref != null) {
            isCheck = mLocalSharedPref.getBoolean(KEY_HAL_ZAL, false);
        }
        SwitchPreference halZSLPref = (SwitchPreference)findPreference(KEY_HAL_ZAL);
        if (halZSLPref != null) {
            halZSLPref.setChecked(isCheck);
        }
    }

    private void initializePictureQuality() {
        String quality = this.getString(R.string.pref_camera_jpegquality_default);
        if (mLocalSharedPref != null) {
            quality = mLocalSharedPref.getString(KEY_PICTURE_QUALITY, quality);
        }
        ListPreference pictureQualityPref = (ListPreference) findPreference(KEY_PICTURE_QUALITY);
        if (pictureQualityPref != null) {
            pictureQualityPref.setValue(quality);
        }
    }

    private void initializePictureSize1() {
        String defaultSize = this.getString(R.string.pref_multi_camera_picturesize_default);
        filterUnsupported(KEY_PICTURE_SIZE_1, getSupportedPictureSize(0));
        String size = null;
        if (mLocalSharedPref != null) {
            size = mLocalSharedPref.getString(KEY_PICTURE_SIZE_1, size);
        }
        ListPreference pictureSizePref = (ListPreference) findPreference(KEY_PICTURE_SIZE_1);
        if (pictureSizePref != null) {
            try {
                if (size == null) {
                    pictureSizePref.setValue(defaultSize);
                } else {
                    pictureSizePref.setValue(size);
                }
            } catch(IndexOutOfBoundsException e) {
            }
        }
    }

    private void initializePictureSize2() {
        String defaultSize = this.getString(R.string.pref_multi_camera_picturesize_default);
        filterUnsupported(KEY_PICTURE_SIZE_2, getSupportedPictureSize(1));
        String size = null;
        if (mLocalSharedPref != null) {
            size = mLocalSharedPref.getString(KEY_PICTURE_SIZE_2, size);
        }
        ListPreference pictureSizePref = (ListPreference) findPreference(KEY_PICTURE_SIZE_2);
        if (pictureSizePref != null) {
            try {
                if (size == null) {
                    pictureSizePref.setValue(defaultSize);
                } else {
                    pictureSizePref.setValue(size);
                }
            } catch(IndexOutOfBoundsException e) {
            }
        }
    }

    private void initializePictureSize3() {
        String defaultSize = this.getString(R.string.pref_multi_camera_picturesize_default);
        filterUnsupported(KEY_PICTURE_SIZE_3, getSupportedPictureSize(2));
        String size = null;
        if (mLocalSharedPref != null) {
            size = mLocalSharedPref.getString(KEY_PICTURE_SIZE_3, size);
        }
        ListPreference pictureSizePref = (ListPreference) findPreference(KEY_PICTURE_SIZE_3);
        if (pictureSizePref != null) {
            try {
                if (size == null) {
                    pictureSizePref.setValue(defaultSize);
                } else {
                    pictureSizePref.setValue(size);
                }
            } catch(IndexOutOfBoundsException e) {
            }
        }
    }

    private void initializeShutterSound() {
        boolean isCheck = false;
        if (mLocalSharedPref != null) {
            isCheck = mLocalSharedPref.getBoolean(KEY_SHUTTER_SOUND, true);
        }
        SwitchPreference shutterSoundPref = (SwitchPreference)findPreference(KEY_SHUTTER_SOUND);
        if (shutterSoundPref != null) {
            shutterSoundPref.setChecked(isCheck);
        }
    }

    private void initializeVideoSize1() {
        String defaultSize = this.getString(R.string.pref_multi_camera_video_quality_default);
        filterUnsupported(KEY_VIDEO_SIZE_1, getSupportedVideoSize(0));
        String size = null;
        if (mLocalSharedPref != null) {
            size = mLocalSharedPref.getString(KEY_VIDEO_SIZE_1, size);
        }
        ListPreference videoSizePref = (ListPreference) findPreference(KEY_VIDEO_SIZE_1);
        if (videoSizePref != null) {
            try {
                if (size == null) {
                    videoSizePref.setValue(defaultSize);
                } else {
                    videoSizePref.setValue(size);
                }
            } catch(IndexOutOfBoundsException e) {
            }
        }
    }

    private void initializeVideoSize2() {
        String defaultSize = this.getString(R.string.pref_multi_camera_video_quality_default);
        filterUnsupported(KEY_VIDEO_SIZE_2, getSupportedVideoSize(1));
        String size = null;
        if (mLocalSharedPref != null) {
            size = mLocalSharedPref.getString(KEY_VIDEO_SIZE_2, size);
        }
        ListPreference videoSizePref = (ListPreference) findPreference(KEY_VIDEO_SIZE_2);
        if (videoSizePref != null) {
            try {
                if (size == null) {
                    videoSizePref.setValue(defaultSize);
                } else {
                    videoSizePref.setValue(size);
                }
            } catch(IndexOutOfBoundsException e) {
            }
        }
    }

    private void initializeVideoSize3() {
        String defaultSize = this.getString(R.string.pref_multi_camera_video_quality_default);
        filterUnsupported(KEY_VIDEO_SIZE_3, getSupportedVideoSize(2));
        String size = null;
        if (mLocalSharedPref != null) {
            size = mLocalSharedPref.getString(KEY_VIDEO_SIZE_3, size);
        }
        ListPreference videoSizePref = (ListPreference) findPreference(KEY_VIDEO_SIZE_3);
        if (videoSizePref != null) {
            try {
                if (size == null) {
                    videoSizePref.setValue(defaultSize);
                } else {
                    videoSizePref.setValue(size);
                }
            } catch(IndexOutOfBoundsException e) {
            }
        }
    }

    private void initializeVideoDuration() {
        filterUnsupported(KEY_VIDEO_DURATION, getSupportedVideoDuration());
        String duration = MultiSettingsActivity.this.getResources().getString(
                R.string.pref_camera_video_duration_default);
        if (mLocalSharedPref != null) {
            duration = mLocalSharedPref.getString(KEY_VIDEO_DURATION, duration);
        }
        ListPreference videoDurationPref = (ListPreference) findPreference(KEY_VIDEO_DURATION);
        if (videoDurationPref != null) {
            try {
                videoDurationPref.setValue(duration);
            } catch(IndexOutOfBoundsException e) {
            }
        }
    }

    private void initializeAudioEncoder() {
        String audioEncoder = MultiSettingsActivity.this.getResources().getString(
                R.string.pref_camera_audioencoder_default);
        if (mLocalSharedPref != null) {
            audioEncoder = mLocalSharedPref.getString(KEY_AUDIO_ENCODER, audioEncoder);
        }
        ListPreference audioEncoderPref = (ListPreference) findPreference(KEY_AUDIO_ENCODER);
        if (audioEncoderPref != null) {
            try {
                audioEncoderPref.setValue(audioEncoder);
            } catch(IndexOutOfBoundsException e) {
            }
        }
    }

    private void initializeVideoRotation() {
        String videoRotation = MultiSettingsActivity.this.getResources().getString(
                R.string.pref_camera_video_rotation_default);
        if (mLocalSharedPref != null) {
            videoRotation = mLocalSharedPref.getString(KEY_VIDEO_ROTATION, videoRotation);
        }
        ListPreference videoRotationPref = (ListPreference) findPreference(KEY_VIDEO_ROTATION);
        if (videoRotationPref != null) {
            try {
                videoRotationPref.setValue(videoRotation);
            } catch(IndexOutOfBoundsException e) {
            }
        }
    }

    private void initializeVersionInfo() {
        // Version Info
        try {
            String versionName = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
            int index = versionName.indexOf(' ');
            versionName = versionName.substring(0, index);
            findPreference(KEY_VERSION_INFO).setSummary(versionName);
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
        }
    }

    private List<String> getSupportedPictureSize(int cameraId) {
        List<String> res = new ArrayList<>();
        try {
            StreamConfigurationMap map = mCharacteristics.get(cameraId).get(
                    CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
            Size[] sizes = map.getOutputSizes(ImageFormat.JPEG);
            Size[] highResSizes = map.getHighResolutionOutputSizes(ImageFormat.JPEG);

            if (sizes != null) {
                for (int i = 0; i < sizes.length; i++) {
                    res.add(sizes[i].toString());
                }
            }

            if (highResSizes != null) {
                for (int i = 0; i < highResSizes.length; i++) {
                    res.add(highResSizes[i].toString());
                }
            }
        } catch (IndexOutOfBoundsException e) {
            e.printStackTrace();
        }

        return res;
    }

    private List<String> getSupportedVideoSize(int cameraId) {
        List<String> res = new ArrayList<>();
        try {
            StreamConfigurationMap map = mCharacteristics.get(cameraId).get(
                    CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
            Size[] sizes = map.getOutputSizes(MediaRecorder.class);
            for (int i = 0; i < sizes.length; i++) {
                if (CameraSettings.VIDEO_QUALITY_TABLE.containsKey(sizes[i].toString())) {
                    Integer profile = CameraSettings.VIDEO_QUALITY_TABLE.get(sizes[i].toString());
                    if (profile != null && CamcorderProfile.hasProfile(cameraId, profile)) {
                        res.add(sizes[i].toString());
                    }
                }
            }
        } catch (IndexOutOfBoundsException e) {
            e.printStackTrace();
        }
        return res;
    }

    private List<String> getSupportedVideoDuration() {
        int[] videoDurations = {-1, 10, 30, 0};
        List<String> modes = new ArrayList<>();
        for (int i : videoDurations) {
            modes.add(""+i);
        }
        return  modes;
    }

    private void filterUnsupported(String key, List<String> supported) {
        ListPreference listPref = (ListPreference) findPreference(key);
        ArrayList<CharSequence> entries = new ArrayList<CharSequence>();
        ArrayList<CharSequence> entryValues = new ArrayList<CharSequence>();
        if (listPref != null) {
            CharSequence[] listEntries = listPref.getEntries();
            CharSequence[] listEntryValues = listPref.getEntryValues();
            for (int i = 0, len = listEntryValues.length; i < len; i++) {
                if (supported.indexOf(listEntryValues[i].toString()) >= 0) {
                    entries.add(listEntries[i]);
                    entryValues.add(listEntryValues[i]);
                }
            }
            int size = entries.size();
            listPref.setEntries(entries.toArray(new CharSequence[size]));
            listPref.setEntryValues(entryValues.toArray(new CharSequence[size]));
        }
    }

    private boolean removePreferenceGroup(String key, PreferenceScreen parentPreferenceScreen) {
        PreferenceGroup removePreference = (PreferenceGroup) findPreference(key);
        if (removePreference != null && parentPreferenceScreen != null) {
            parentPreferenceScreen.removePreference(removePreference);
            return true;
        }
        return false;
    }

    private void onRestoreDefaultSettingsClick() {
        new AlertDialog.Builder(this)
                .setMessage(R.string.pref_camera2_restore_default_hint)
                .setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        restoreSettings();
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void restoreSettings() {
        clearPerCameraPreferences();
        filterPreferences();
        initializePreferences();
    }

    private void clearPerCameraPreferences() {
        ArrayList<String> prepNameKeys = new ArrayList<>();
        prepNameKeys.add("multi" + String.valueOf(MultiCameraModule.CameraMode.DEFAULT));
        prepNameKeys.add("multi" + String.valueOf(MultiCameraModule.CameraMode.VIDEO));
        String[] preferencesNames = ComboPreferences.getSharedPreferencesNames(this, prepNameKeys);
        for ( String name : preferencesNames ) {
            SharedPreferences.Editor editor =
                    getSharedPreferences(name, Context.MODE_PRIVATE).edit();
            editor.clear();
            editor.commit();
        }
    }
}
