 /*
 * Copyright (c) 2016-2017, The Linux Foundation. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are
 * met:
 *  * Redistributions of source code must retain the above copyright
 *    notice, this list of conditions and the following disclaimer.
 *  * Redistributions in binary form must reproduce the above
 *    copyright notice, this list of conditions and the following
 *    disclaimer in the documentation and/or other materials provided
 *    with the distribution.
 *  * Neither the name of The Linux Foundation nor the names of its
 *    contributors may be used to endorse or promote products derived
 *    from this software without specific prior written permission.
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

package com.android.camera;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.MediaCodecInfo;
import android.media.MediaCodecInfo.CodecCapabilities;
import android.media.MediaCodecInfo.VideoCapabilities;
import android.media.MediaCodecList;
import android.media.MediaFormat;
import android.media.MediaRecorder;
import android.media.CamcorderProfile;
import android.preference.PreferenceManager;
import android.util.Log;
import android.util.Range;
import android.util.Rational;
import android.util.Size;
import android.media.EncoderCapabilities;
import android.media.EncoderCapabilities.VideoEncoderCap;

import com.android.camera.imageprocessor.filter.BeautificationFilter;
import com.android.camera.imageprocessor.filter.BestpictureFilter;
import com.android.camera.imageprocessor.filter.BlurbusterFilter;
import com.android.camera.imageprocessor.filter.ChromaflashFilter;
import com.android.camera.imageprocessor.filter.DeepPortraitFilter;
import com.android.camera.imageprocessor.filter.OptizoomFilter;
import com.android.camera.imageprocessor.filter.SharpshooterFilter;
import com.android.camera.imageprocessor.filter.StillmoreFilter;
import com.android.camera.imageprocessor.filter.TrackingFocusFrameListener;
import com.android.camera.imageprocessor.filter.UbifocusFilter;
import com.android.camera.imageprocessor.filter.DeepZoomFilter;
import com.android.camera.ui.ListMenu;
import com.android.camera.ui.PanoCaptureProcessView;
import com.android.camera.ui.TrackingFocusRenderer;
import com.android.camera.util.SettingTranslation;
import com.android.camera.app.CameraApp;
import com.android.camera.util.AutoTestUtil;

import org.codeaurora.snapcam.R;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.io.BufferedReader;
import java.io.InputStream;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.lang.NumberFormatException;
import java.lang.StringBuilder;

public class SettingsManager implements ListMenu.SettingsListener {
    public static final int RESOURCE_TYPE_THUMBNAIL = 0;
    public static final int RESOURCE_TYPE_LARGEICON = 1;

    public static final int SCENE_MODE_AUTO_INT = 0;
    public static final int SCENE_MODE_NIGHT_INT = 5;
    public static final int SCENE_MODE_HDR_INT = 18;

    public static final int TALOS_SOCID = 355;
    public static final int MOOREA_SOCID = 365;
    public static final int SAIPAN_SOCID = 400;
    public static final int SM6250_SOCID = 407;

    // Custom-Scenemodes start from 100
    public static final int SCENE_MODE_CUSTOM_START = 100;
    public static final int SCENE_MODE_DUAL_INT = SCENE_MODE_CUSTOM_START;
    public static final int SCENE_MODE_OPTIZOOM_INT = SCENE_MODE_CUSTOM_START + 1;
    public static final int SCENE_MODE_UBIFOCUS_INT = SCENE_MODE_CUSTOM_START + 2;
    public static final int SCENE_MODE_BESTPICTURE_INT = SCENE_MODE_CUSTOM_START + 3;
    public static final int SCENE_MODE_PANORAMA_INT = SCENE_MODE_CUSTOM_START + 4;
    public static final int SCENE_MODE_CHROMAFLASH_INT = SCENE_MODE_CUSTOM_START + 5;
    public static final int SCENE_MODE_BLURBUSTER_INT = SCENE_MODE_CUSTOM_START + 6;
    public static final int SCENE_MODE_SHARPSHOOTER_INT = SCENE_MODE_CUSTOM_START + 7;
    public static final int SCENE_MODE_TRACKINGFOCUS_INT = SCENE_MODE_CUSTOM_START + 8;
    public static final int SCENE_MODE_PROMODE_INT = SCENE_MODE_CUSTOM_START + 9;
    public static final int SCENE_MODE_DEEPZOOM_INT = SCENE_MODE_CUSTOM_START + 10;
	public static final int SCENE_MODE_DEEPPORTRAIT_INT = SCENE_MODE_CUSTOM_START + 11;
    public static final int JPEG_FORMAT = 0;
    public static final int HEIF_FORMAT = 1;
    public static final String SCENE_MODE_DUAL_STRING = "100";
    public static final String SCENE_MODE_SUNSET_STRING = "10";
    public static final String SCENE_MODE_LANDSCAPE_STRING = "4";
    public static final String KEY_RECORD_LOCATION = "pref_camera2_recordlocation_key";
    public static final String KEY_JPEG_QUALITY = "pref_camera2_jpegquality_key";
    public static final String KEY_FOCUS_MODE = "pref_camera2_focusmode_key";
    public static final String KEY_FLASH_MODE = "pref_camera2_flashmode_key";
    public static final String KEY_WHITE_BALANCE = "pref_camera2_whitebalance_key";
    public static final String KEY_MAKEUP = "pref_camera2_makeup_key";
    public static final String KEY_MONO_ONLY = "pref_camera2_mono_only_key";
    public static final String KEY_MONO_PREVIEW = "pref_camera2_mono_preview_key";
    public static final String KEY_CLEARSIGHT = "pref_camera2_clearsight_key";
    public static final String KEY_MPO = "pref_camera2_mpo_key";
    public static final String KEY_FILTER_MODE = "pref_camera2_filter_mode_key";
    public static final String KEY_COLOR_EFFECT = "pref_camera2_coloreffect_key";
    public static final String KEY_SCENE_MODE = "pref_camera2_scenemode_key";
    public static final String KEY_SCEND_MODE_INSTRUCTIONAL = "pref_camera2_scenemode_instructional";
    public static final String KEY_REDEYE_REDUCTION = "pref_camera2_redeyereduction_key";
    public static final String KEY_CAMERA_ID = "pref_camera2_id_key";
    public static final String KEY_SWITCH_CAMERA = "pref_camera2_switch_camera_key";
    public static final String KEY_PICTURE_SIZE = "pref_camera2_picturesize_key";
    public static final String KEY_PICTURE_FORMAT = "pref_camera2_picture_format_key";
    public static final String KEY_ISO = "pref_camera2_iso_key";
    public static final String KEY_EXPOSURE = "pref_camera2_exposure_key";
    public static final String KEY_TIMER = "pref_camera2_timer_key";
    public static final String KEY_LONGSHOT = "pref_camera2_longshot_key";
    public static final String KEY_SELFIEMIRROR = "pref_camera2_selfiemirror_key";
    public static final String KEY_VIDEO_DURATION = "pref_camera2_video_duration_key";
    public static final String KEY_VIDEO_QUALITY = "pref_camera2_video_quality_key";
    public static final String KEY_VIDEO_ENCODER = "pref_camera2_videoencoder_key";
    public static final String KEY_AUDIO_ENCODER = "pref_camera2_audioencoder_key";
    public static final String KEY_DIS = "pref_camera2_dis_key";
    public static final String KEY_NOISE_REDUCTION = "pref_camera2_noise_reduction_key";
    public static final String KEY_VIDEO_FLASH_MODE = "pref_camera2_video_flashmode_key";
    public static final String KEY_VIDEO_ROTATION = "pref_camera2_video_rotation_key";
    public static final String KEY_VIDEO_TIME_LAPSE_FRAME_INTERVAL =
            "pref_camera2_video_time_lapse_frame_interval_key";
    public static final String KEY_FACE_DETECTION = "pref_camera2_facedetection_key";
    public static final String KEY_VIDEO_HIGH_FRAME_RATE = "pref_camera2_hfr_key";
    public static final String KEY_SELFIE_FLASH = "pref_selfie_flash_key";
    public static final String KEY_SHUTTER_SOUND = "pref_camera2_shutter_sound_key";
    public static final String KEY_DEVELOPER_MENU = "pref_camera2_developer_menu_key";
    public static final String KEY_RESTORE_DEFAULT = "pref_camera2_restore_default_key";
    public static final String KEY_FOCUS_DISTANCE = "pref_camera2_focus_distance_key";
    public static final String KEY_INSTANT_AEC = "pref_camera2_instant_aec_key";
    public static final String KEY_SATURATION_LEVEL = "pref_camera2_saturation_level_key";
    public static final String KEY_ANTI_BANDING_LEVEL = "pref_camera2_anti_banding_level_key";
    public static final String KEY_AUTO_HDR = "pref_camera2_auto_hdr_key";
    public static final String KEY_HDR = "pref_camera2_hdr_key";
    public static final String KEY_VIDEO_HDR_VALUE = "pref_camera2_video_hdr_key";
    public static final String KEY_CAPTURE_MFNR_VALUE = "pref_camera2_capture_mfnr_key";
    public static final String KEY_SENSOR_MODE_FS2_VALUE = "pref_camera2_fs2_key";
    public static final String KEY_ABORT_CAPTURES = "pref_camera2_abort_captures_key";
    public static final String KEY_SAVERAW = "pref_camera2_saveraw_key";
    public static final String KEY_ZOOM = "pref_camera2_zoom_key";
    public static final String KEY_SHARPNESS_CONTROL_MODE = "pref_camera2_sharpness_control_key";
    public static final String KEY_AF_MODE = "pref_camera2_afmode_key";
    public static final String KEY_EXPOSURE_METERING_MODE = "pref_camera2_exposure_metering_key";

    //manual 3A keys and parameter strings
    public static final String KEY_MANUAL_EXPOSURE = "pref_camera2_manual_exp_key";
    public static final String KEY_MANUAL_ISO_VALUE = "pref_camera2_manual_iso_key";
    public static final String KEY_MANUAL_GAINS_VALUE = "pref_camera2_manual_gains_key";
    public static final String KEY_MANUAL_EXPOSURE_VALUE = "pref_camera2_manual_exposure_key";

    public static final String AUTO_TEST_WRITE_CONTENT = "auto_test_write_content";

    public static final String KEY_MANUAL_WB = "pref_camera2_manual_wb_key";
    public static final String KEY_MANUAL_WB_TEMPERATURE_VALUE =
            "pref_camera2_manual_temperature_key";
    public static final String KEY_MANUAL_WB_R_GAIN = "pref_camera2_manual_wb_r_gain";
    public static final String KEY_MANUAL_WB_G_GAIN = "pref_camera2_manual_wb_g_gain";
    public static final String KEY_MANUAL_WB_B_GAIN = "pref_camera2_manual_wb_b_gain";

    public static final String KEY_QCFA = "pref_camera2_qcfa_key";
    public static final String KEY_EIS_VALUE = "pref_camera2_eis_key";
    public static final String KEY_FOVC_VALUE = "pref_camera2_fovc_key";
    public static final String KEY_DEEPPORTRAIT_VALUE = "pref_camera2_deepportrait_key";
    public static final String KEY_AWB_RAGIN_VALUE = "pref_camera2_awb_cct_rgain_key";
    public static final String KEY_AWB_GAGIN_VALUE = "pref_camera2_awb_cct_ggain_key";
    public static final String KEY_AWB_BAGIN_VALUE = "pref_camera2_awb_cct_bgain_key";
    public static final String KEY_AWB_CCT_VALUE = "pref_camera2_awb_cct_key";
    public static final String KEY_AWB_DECISION_AFTER_TC_0 = "pref_camera2_awb_decision_after_tc_0";
    public static final String KEY_AWB_DECISION_AFTER_TC_1 = "pref_camera2_awb_decision_after_tc_1";
    public static final String KEY_AEC_SENSITIVITY_0 = "pref_camera2_aec_sensitivity_0";
    public static final String KEY_AEC_SENSITIVITY_1 = "pref_camera2_aec_sensitivity_1";
    public static final String KEY_AEC_SENSITIVITY_2 = "pref_camera2_aec_sensitivity_2";
    public static final String KEY_STATS_VISUALIZER_VALUE = "pref_camera2_stats_visualizer_key";

    public static final HashMap<String, Integer> KEY_ISO_INDEX = new HashMap<String, Integer>();
    public static final String KEY_BSGC_DETECTION = "pref_camera2_bsgc_key";
    public static final String KEY_ZSL = "pref_camera2_zsl_key";
    public static final String KEY_VIDEO_ENCODER_PROFILE = "pref_camera2_videoencoderprofile_key";
    public static final String MAUNAL_ABSOLUTE_ISO_VALUE = "absolute";

    private static final String TAG = "SnapCam_SettingsManager";

    private static SettingsManager sInstance;
    private ArrayList<CameraCharacteristics> mCharacteristics;
    private ArrayList<Listener> mListeners;
    private Map<String, Values> mValuesMap;
    private Context mContext;
    private PreferenceGroup mPreferenceGroup;
    private ComboPreferences mPreferences;
    private Map<String, Set<String>> mDependendsOnMap;
    private boolean mIsMonoCameraPresent = false;
    private boolean mIsFrontCameraPresent = false;
    private JSONObject mDependency;
    private int mCameraId;
    private Set<String> mFilteredKeys;
    private int[] mExtendedHFRSize;//An array of pairs (fps, maxW, maxH)
    private int mDeviceSocId = -1;

    private static Map<String, Set<String>> VIDEO_ENCODER_PROFILE_TABLE = new HashMap<>();

    public Map<String, Values> getValuesMap() {
        return mValuesMap;
    }

    public Set<String> getFilteredKeys() {
        return mFilteredKeys;
    }

    static {
        //ISO values vendor tag
        KEY_ISO_INDEX.put("auto", 0);
        KEY_ISO_INDEX.put("deblur", 1);
        KEY_ISO_INDEX.put("100", 2);
        KEY_ISO_INDEX.put("200", 3);
        KEY_ISO_INDEX.put("400", 4);
        KEY_ISO_INDEX.put("800", 5);
        KEY_ISO_INDEX.put("1600", 6);
        KEY_ISO_INDEX.put("3200", 7);
        KEY_ISO_INDEX.put(MAUNAL_ABSOLUTE_ISO_VALUE, 8);
        Set<String> h265 = new HashSet<>();
        h265.add("HEVCProfileMain10");
        h265.add("HEVCProfileMain10HDR10");
        VIDEO_ENCODER_PROFILE_TABLE.put("h265", h265);
    }

    private SettingsManager(Context context) {
        mListeners = new ArrayList<>();
        mCharacteristics = new ArrayList<>();
        mContext = context;
        mPreferences = ComboPreferences.get(mContext);
        if (mPreferences == null) {
            mPreferences = new ComboPreferences(mContext);
        }
        upgradeGlobalPreferences(mPreferences.getGlobal(), mContext);

        CameraManager manager = (CameraManager) mContext.getSystemService(Context.CAMERA_SERVICE);
        try {
            String[] cameraIdList = manager.getCameraIdList();
            boolean isFirstBackCameraId = true;
            for (int i = 0; i < cameraIdList.length; i++) {
                String cameraId = cameraIdList[i];
                CameraCharacteristics characteristics
                        = manager.getCameraCharacteristics(cameraId);
                Log.d(TAG,"cameraIdList size ="+cameraIdList.length);
                byte monoOnly = 0;
                try {
                    monoOnly = characteristics.get(CaptureModule.MetaDataMonoOnlyKey);
                }catch(Exception e) {
                }
                if (monoOnly == 1) {
                    CaptureModule.MONO_ID = i;
                    mIsMonoCameraPresent = true;
                }
                int facing = characteristics.get(CameraCharacteristics.LENS_FACING);
                if (facing == CameraCharacteristics.LENS_FACING_FRONT) {
                    CaptureModule.FRONT_ID = i;
                    mIsFrontCameraPresent = true;
                } else if (facing == CameraCharacteristics.LENS_FACING_BACK &&
                        isFirstBackCameraId) {
                    isFirstBackCameraId = false;
                    upgradeCameraId(mPreferences.getGlobal(), i);
                }
                mCharacteristics.add(i, characteristics);
            }
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }

        mDependency = parseJson("dependency.json");
    }

    public static SettingsManager createInstance(Context context) {
        if (sInstance == null) {
            sInstance = new SettingsManager(context.getApplicationContext());
        }
        return sInstance;
    }

    public static SettingsManager getInstance() {
        return sInstance;
    }

    public void destroyInstance() {
        if (sInstance != null) {
            sInstance = null;
        }
    }

    private void upgradeGlobalPreferences(SharedPreferences pref, Context context) {
        CameraSettings.upgradeOldVersion(pref, context);
    }

    private void upgradeCameraId(SharedPreferences pref, int id) {
        CameraSettings.writePreferredCameraId(pref, id);
    }

    public List<String> getDisabledList() {
        List<String> list = new ArrayList<>();
        Set<String> keySet = mValuesMap.keySet();
        for (String key : keySet) {
            Values value = mValuesMap.get(key);
            if (value.overriddenValue != null) {
                list.add(key);
            }
        }
        return list;
    }

    @Override
    public void onSettingChanged(ListPreference pref) {
        String key = pref.getKey();
        List changed = checkDependencyAndUpdate(key);
        if (changed == null) return;
        runTimeUpdateDependencyOptions(pref);
        notifyListeners(changed);
    }

    public void updatePictureAndVideoSize() {
        ListPreference pictureSize = mPreferenceGroup.findPreference(KEY_PICTURE_SIZE);
        ListPreference videoQualityPref = mPreferenceGroup.findPreference(KEY_VIDEO_QUALITY);
        if (pictureSize != null) {
            CameraSettings.formatPictureSizes(pictureSize,
                    getSupportedPictureSizeList(getCurrentCameraId()), mContext);
            CameraSettings.resetIfInvalid(pictureSize);
        }
        if (videoQualityPref != null) {
            videoQualityPref.setEntries(mContext.getResources().getStringArray(
                    R.array.pref_camera2_video_quality_entries));
            videoQualityPref.setEntryValues(mContext.getResources().getStringArray(
                    R.array.pref_camera2_video_quality_entryvalues));
            filterUnsupportedOptions(videoQualityPref,getSupportedVideoSize(
                    getCurrentCameraId()));
        }
    }

    public void init() {
        Log.d(TAG, "SettingsManager init");
        final int cameraId = getInitialCameraId(mPreferences);
        setLocalIdAndInitialize(cameraId);
        autoTestBroadcast(cameraId);
    }

    public void reinit(int cameraId) {
        Log.d(TAG, "SettingsManager reinit " + cameraId);
        setLocalIdAndInitialize(cameraId);
    }

    private void autoTestBroadcast(int cameraId) {
        final SharedPreferences pref = mContext.getSharedPreferences(
                ComboPreferences.getLocalSharedPreferencesName(mContext, cameraId),
                Context.MODE_PRIVATE);
        final SharedPreferences.Editor editor = pref.edit();
        boolean autoWrite = pref.getBoolean(AUTO_TEST_WRITE_CONTENT, true);
        if (autoWrite) {
            Thread autoTest = new Thread() {
                public void run() {
                    writeAutoTextHelpTxt(editor);
                }
            };
            autoTest.start();
        }
    }

    private void writeAutoTextHelpTxt(SharedPreferences.Editor editor) {
        List<String> supportLists = new ArrayList<String>();
        /* Video Size */
        String[] videoSizes = getEntryValues(R.array.pref_camera2_video_quality_entryvalues);
        /* Picture Size */
        ListPreference pictureSize = mPreferenceGroup.findPreference(KEY_PICTURE_SIZE);
        CameraSettings.formatPictureSizes(pictureSize,
                getSupportedPictureSizeList(getCurrentCameraId()), mContext);
        CameraSettings.resetIfInvalid(pictureSize);
        String[] pictSizes = convertToStringArray(pictureSize.getEntryValues());
        // back support pictureSizes
        List<String> backPLists = getSupportList(getSupportedPictureSize(0), pictSizes);
        supportLists.add("<Back camera support PictureSizes>");
        supportLists.addAll(backPLists);
        // front support pictureSizes
        if (mCharacteristics.size() > 1) {
            List<String> frontPLists = getSupportList(getSupportedPictureSize(1), pictSizes);
            supportLists.add("<Front camera support PictureSizes>");
            supportLists.addAll(frontPLists);
            /* Video Size */
            List<String> frontVideoLists = getSupportList(getSupportedVideoSize(1), videoSizes);
            supportLists.add("<Front camera support VideoSizes and fps>");
            for (int i=0; i < frontVideoLists.size(); i++) {
                String videoSize = frontVideoLists.get(i);
                List<String> fps = getSupportedHFRForAutoTest(videoSize);
                supportLists.add(videoSize);
                supportLists.addAll(fps);
                List<String> videoEncoders = getSupportedVideoEncoderForAutoTest(videoSize);
                supportLists.addAll(videoEncoders);
                supportLists.add("");
            }
        }
        List<String> backVideoLists = getSupportList(getSupportedVideoSize(0), videoSizes);
        supportLists.add("<Back camera support VideoSizes and fps>");
        for (int i=0; i < backVideoLists.size(); i++) {
            String videoSize = backVideoLists.get(i);
            List<String> fps = getSupportedHFRForAutoTest(videoSize);
            supportLists.add(videoSize);
            supportLists.addAll(fps);
            List<String> videoEncoders = getSupportedVideoEncoderForAutoTest(videoSize);
            supportLists.addAll(videoEncoders);
            supportLists.add("");
        }

        String filePath = AutoTestUtil.createFile(mContext);
        boolean result = AutoTestUtil.writeFileContent(filePath, supportLists);
        editor.putBoolean(AUTO_TEST_WRITE_CONTENT, false);
        editor.apply();
    }

    private List<String> setCharSequenceToListStr(String title, CharSequence[] charSequences) {
        List<String> list = new ArrayList<String>();
        list.add(title);
        for (CharSequence support : charSequences) {
            list.add(support.toString());
        }
        return list;
    }

    private String[] convertToStringArray(CharSequence[] charSequences) {
        String[] resultStr = new String[charSequences.length];
        for (int i = 0; i < charSequences.length; i++) {
	        resultStr[i] = charSequences[i].toString();
        }

        return resultStr;
    }

    private String[] getEntryValues(int id) {
        return mContext.getResources().getStringArray(id);

    }

    public List<String> getSupportList(List<String> supported, String[] supportList) {
        List<String> resultList = new ArrayList<String>();
        for (String item : supportList) {
            if (supported.indexOf(item) >= 0) {
                resultList.add(item);
            }
        }
        return resultList;
    }

    private void setLocalIdAndInitialize(int cameraId) {
        mPreferences.setLocalId(mContext, cameraId);
        mCameraId = cameraId;
        CameraSettings.upgradeLocalPreferences(mPreferences.getLocal());

        PreferenceInflater inflater = new PreferenceInflater(mContext);
        mPreferenceGroup =
                (PreferenceGroup) inflater.inflate(R.xml.capture_preferences);
        mValuesMap = new HashMap<>();
        mDependendsOnMap = new HashMap<>();
        mFilteredKeys = new HashSet<>();
        try {
            if (mCharacteristics.size() > 0) {
                mExtendedHFRSize = mCharacteristics.get(cameraId).get(CaptureModule.hfrFpsTable);
            }
        }catch(IllegalArgumentException exception) {
            exception.printStackTrace();
        }

        filterPreferences(cameraId);
        initDependencyTable();
        initializeValueMap();
        filterChromaflashPictureSizeOptions();
        filterHeifSizeOptions();
    }

    private Size parseSize(String value) {
        int indexX = value.indexOf('x');
        int width = Integer.parseInt(value.substring(0, indexX));
        int height = Integer.parseInt(value.substring(indexX + 1));
        return new Size(width, height);
    }

    private void initDependencyTable() {
        for (int i = 0; i < mPreferenceGroup.size(); i++) {
            ListPreference pref = (ListPreference) mPreferenceGroup.get(i);
            String baseKey = pref.getKey();
            String value = pref.getValue();

            JSONObject dependency = getDependencyList(baseKey, value);
            if (dependency != null) {
                Iterator<String> keys = dependency.keys();
                while (keys.hasNext()) {
                    String key = keys.next();
                    pref = mPreferenceGroup.findPreference(key);
                    if (pref == null) continue; //filtered?
                    Set set = mDependendsOnMap.get(key);
                    if (set == null) {
                        set = new HashSet<>();
                    }
                    set.add(baseKey);
                    mDependendsOnMap.put(key, set);
                }
            }
        }
    }

    private void initializeValueMap() {
        List<String> processLater = new ArrayList<String>();
        for (int i = 0; i < mPreferenceGroup.size(); i++) {
            ListPreference pref = (ListPreference) mPreferenceGroup.get(i);
            String key = pref.getKey();
            Set<String> set = mDependendsOnMap.get(key);
            if (set != null && set.size() != 0) {
                processLater.add(key);
            }
            Values values = new Values(pref.getValue(), null);
            mValuesMap.put(pref.getKey(), values);
        }

        for (String keyToProcess : processLater) {
            Set<String> dependsOnSet = mDependendsOnMap.get(keyToProcess);
            String dependentKey = dependsOnSet.iterator().next();
            String value = getValue(dependentKey);
            JSONObject dependencyList = getDependencyList(dependentKey, value);

            String newValue = null;
            try {
                newValue = dependencyList.getString(keyToProcess);
            } catch (JSONException e) {
                Log.w(TAG, "initializeValueMap JSONException No value for:" + keyToProcess);
                continue;
            }
            Values values = new Values(getValue(keyToProcess), newValue);
            mValuesMap.put(keyToProcess, values);
        }
    }

    private List<SettingState> checkDependencyAndUpdate(String changedPrefKey) {
        ListPreference changedPref = mPreferenceGroup.findPreference(changedPrefKey);
        if (changedPref == null) return null;

        String value = changedPref.getValue();
        String prevValue = getValue(changedPrefKey);
        if (value.equals(prevValue)) return null;

        List<SettingState> changed = new ArrayList();
        Values values = new Values(value, null);
        mValuesMap.put(changedPrefKey, values);
        changed.add(new SettingState(changedPrefKey, values));

        JSONObject map = getDependencyMapForKey(changedPrefKey);
        if (map == null || getDependencyKey(map, value).equals(getDependencyKey(map,prevValue)))
            return changed;

        Set<String> turnOn = new HashSet<>();
        Set<String> turnOff = new HashSet<>();

        JSONObject dependencyList = getDependencyList(changedPrefKey, value);
        JSONObject originalDependencyList = getDependencyList(changedPrefKey, prevValue);

        Iterator<String> it = originalDependencyList.keys();
        while (it.hasNext()) {
            turnOn.add(it.next());
        }
        it = dependencyList.keys();
        while (it.hasNext()) {
            turnOff.add(it.next());
        }
        it = originalDependencyList.keys();
        while (it.hasNext()) {
            turnOff.remove(it.next());
        }
        it = dependencyList.keys();
        while (it.hasNext()) {
            turnOn.remove(it.next());
        }

        for (String keyToTurnOn: turnOn) {
            Set<String> dependsOnSet = mDependendsOnMap.get(keyToTurnOn);
            if (dependsOnSet == null || dependsOnSet.size() == 0) continue;

                values = mValuesMap.get(keyToTurnOn);
                if (values == null) continue;
                values.overriddenValue = null;
                mValuesMap.put(keyToTurnOn, values);
                changed.add(new SettingState(keyToTurnOn, values));
        }

        for (String keyToTurnOff: turnOff) {
            ListPreference pref = mPreferenceGroup.findPreference(keyToTurnOff);
            if (pref == null) continue;
            values = mValuesMap.get(keyToTurnOff);
            if (values == null) continue;
            if (values != null && values.overriddenValue != null) continue;
            String newValue = null;
            try {
                newValue = dependencyList.getString(keyToTurnOff);
            } catch (JSONException e) {
                Log.w(TAG, "checkDependencyAndUpdate JSONException No value for:" + keyToTurnOff);
                continue;
            }
            if (newValue == null) continue;

            Values newValues = new Values(pref.getValue(), newValue);
            mValuesMap.put(keyToTurnOff, newValues);
            changed.add(new SettingState(keyToTurnOff, newValues));
        }
            updateBackDependency(changedPrefKey, turnOn, turnOff);
        return changed;
    }

    private void updateBackDependency(String key, Set<String> remove, Set<String> add) {
        for (CharSequence c : remove) {
            String currentKey = c.toString();
            Set<String> dependsOnSet = mDependendsOnMap.get(currentKey);
            if (dependsOnSet != null) dependsOnSet.remove(key);
        }
        for (CharSequence c : add) {
            String currentKey = c.toString();
            Set<String> dependsOnSet = mDependendsOnMap.get(currentKey);
            if (dependsOnSet == null) {
                dependsOnSet = new HashSet<>();
                mDependendsOnMap.put(currentKey, dependsOnSet);
            }
            dependsOnSet.add(key);
        }
    }

    public int[] getSensorModeTable(final int cameraId) {
        int[] sensorModeTable = null;
        try {
            sensorModeTable = mCharacteristics.get(cameraId).get(CaptureModule.sensorModeTable);
        } catch (IllegalArgumentException exception) {
            exception.printStackTrace();
        }
        return sensorModeTable;
    }

    public int[] getHighSpeedVideoConfigs(final int cameraId) {
        int[] highSpeedVideoConfigs = null;
        try {
            highSpeedVideoConfigs = mCharacteristics.get(cameraId).get(
                    CaptureModule.highSpeedVideoConfigs);
        } catch (IllegalArgumentException exception) {
            exception.printStackTrace();
        }
        return highSpeedVideoConfigs;
    }

    public void registerListener(Listener listener) {
        mListeners.add(listener);
    }

    public void unregisterListener(Listener listener) {
        mListeners.remove(listener);
    }

    private void notifyListeners(List<SettingState> changes) {
        for (Listener listener : mListeners) {
            listener.onSettingsChanged(changes);
        }
    }

    public int getDeviceSocId() {
        if (mDeviceSocId == -1) {
            String filePath = "/sys/devices/soc0/soc_id";
            File file = new File(filePath);
            if (file.isDirectory()) {
                Log.d(TAG, filePath + " is directory");
            } else {
                try {
                    InputStream is = new FileInputStream(file);
                    if (is != null) {
                        InputStreamReader isr = new InputStreamReader(is);
                        BufferedReader br = new BufferedReader(isr);

                        String line;
                        while ((line = br.readLine()) != null) {
                            mDeviceSocId = Integer.parseInt(line);
                        }
                    }
                } catch (FileNotFoundException e) {
                    Log.d(TAG, filePath + " doesn't found!");
                } catch (IOException | NumberFormatException e) {
                    Log.d(TAG, filePath + " read exception, " + e.getMessage());
                }
            }
        }
        Log.d(TAG, "getDeviceSocId mDeviceSocId :" + mDeviceSocId);
        return mDeviceSocId;
    }

    public int getCurrentCameraId() {
        return mCameraId;
    }

    public String getValue(String key) {
        if (mValuesMap == null) return null;
        Values values = mValuesMap.get(key);
        if (values == null) return null;
        if (values.overriddenValue == null) return values.value;
        else return values.overriddenValue;
    }

    public int getValueIndex(String key) {
        ListPreference pref = mPreferenceGroup.findPreference(key);
        String value = getValue(key);
        if ((value == null) || (pref == null)) return -1;
        return pref.findIndexOfValue(value);
    }

    private boolean setFocusValue(String key, float value) {
        boolean result = false;
        String prefName = ComboPreferences.getLocalSharedPreferencesName(mContext, mCameraId);
        SharedPreferences sharedPreferences = mContext.getSharedPreferences(prefName,
                Context.MODE_PRIVATE);
        float prefValue = sharedPreferences.getFloat(key, 0.5f);
        if (prefValue != value) {
            SharedPreferences.Editor editor = sharedPreferences.edit();
            editor.putFloat(key, value);
            editor.apply();
            result = true;
        }
        return result;
    }

    public float getFocusValue(String key) {
        String prefName = ComboPreferences.getLocalSharedPreferencesName(mContext, mCameraId);
        SharedPreferences sharedPreferences = mContext.getSharedPreferences(prefName,
                Context.MODE_PRIVATE);
        return sharedPreferences.getFloat(key, 0.5f);
    }

    public boolean isOverriden(String key) {
        Values values = mValuesMap.get(key);
        return values.overriddenValue != null;
    }

    public boolean setValue(String key, String value) {
        if (mPreferenceGroup != null) {
            ListPreference pref = mPreferenceGroup.findPreference(key);
            if (pref != null) {
                if (pref.findIndexOfValue(value) < 0) {
                    return false;
                } else {
                    pref.setValue(value);
                    updateMapAndNotify(pref);
                    return true;
                }
            }
        }
        return false;
    }

    public boolean setValue(String key, Set<String> set) {
        ListPreference pref = mPreferenceGroup.findPreference(key);
        if (pref != null) {
            pref.setFromMultiValues(set);
            updateMapAndNotify(pref);
            return true;
        } else {
            return false;
        }
    }

    public void setValueIndex(String key, int index) {
        ListPreference pref = mPreferenceGroup.findPreference(key);
        if (pref != null) {
            pref.setValueIndex(index);
            updateMapAndNotify(pref);
        }
    }

    public void setFocusDistance(String key, boolean forceNotify, float value, float minFocus) {
        boolean isSuccess = false;
        if (value >= 0) {
            isSuccess = setFocusValue(key, value * minFocus);
        }
        if (isSuccess || forceNotify) {
            List<SettingState> list = new ArrayList<>();
            Values values = new Values("" + value * minFocus, null);
            SettingState ss = new SettingState(KEY_FOCUS_DISTANCE, values);
            list.add(ss);
            notifyListeners(list);
        }
    }

    private void updateMapAndNotify(ListPreference pref) {
        String key = pref.getKey();
        List changed = checkDependencyAndUpdate(key);
        if (changed == null) return;
        runTimeUpdateDependencyOptions(pref);
        notifyListeners(changed);
    }

    public PreferenceGroup getPreferenceGroup() {
        return mPreferenceGroup;
    }

    public CharSequence[] getEntries(String key) {
        if ( mPreferenceGroup != null ) {
            ListPreference pref = mPreferenceGroup.findPreference(key);
            if (pref != null) {
                return pref.getEntries();
            }
        }
        return null;
    }

    public CharSequence[] getEntryValues(String key) {
        ListPreference pref = mPreferenceGroup.findPreference(key);
        if (pref != null) {
            return pref.getEntryValues();
        }
        return null;
    }

    public int[] getResource(String key, int type) {
        IconListPreference pref = (IconListPreference) mPreferenceGroup.findPreference(key);
        switch (type) {
            case RESOURCE_TYPE_THUMBNAIL:
                return pref.getThumbnailIds();
            case RESOURCE_TYPE_LARGEICON:
                return pref.getLargeIconIds();
        }
        return null;
    }

    public int getInitialCameraId(SharedPreferences pref) {
        int switchId = Integer.parseInt(
                pref.getString(SettingsManager.KEY_SWITCH_CAMERA,"-1"));
        CaptureModule.SWITCH_ID = switchId;
        Log.d(TAG,"SWITCH_ID = " + switchId);
        if (switchId != -1) return switchId;
        String value = pref.getString(SettingsManager.KEY_CAMERA_ID, "0");
        int frontBackId = Integer.parseInt(value);
        if (frontBackId == CaptureModule.FRONT_ID) return frontBackId;
        String monoOnly = pref.getString(SettingsManager.KEY_MONO_ONLY, "off");
        if (monoOnly.equals("off")) return frontBackId;
        else return CaptureModule.MONO_ID;
    }

    private void filterPreferences(int cameraId) {
        // filter unsupported preferences
        ListPreference whiteBalance = mPreferenceGroup.findPreference(KEY_WHITE_BALANCE);
        ListPreference flashMode = mPreferenceGroup.findPreference(KEY_FLASH_MODE);
        ListPreference colorEffect = mPreferenceGroup.findPreference(KEY_COLOR_EFFECT);
        ListPreference sceneMode = mPreferenceGroup.findPreference(KEY_SCENE_MODE);
        ListPreference sceneModeInstructional =
                mPreferenceGroup.findPreference(KEY_SCEND_MODE_INSTRUCTIONAL);

        ListPreference cameraIdPref = mPreferenceGroup.findPreference(KEY_CAMERA_ID);
        ListPreference pictureSize = mPreferenceGroup.findPreference(KEY_PICTURE_SIZE);
        ListPreference exposure = mPreferenceGroup.findPreference(KEY_EXPOSURE);
        ListPreference iso = mPreferenceGroup.findPreference(KEY_ISO);
        ListPreference clearsight = mPreferenceGroup.findPreference(KEY_CLEARSIGHT);
        ListPreference monoPreview = mPreferenceGroup.findPreference(KEY_MONO_PREVIEW);
        ListPreference monoOnly = mPreferenceGroup.findPreference(KEY_MONO_ONLY);
        ListPreference mpo = mPreferenceGroup.findPreference(KEY_MPO);
        ListPreference redeyeReduction = mPreferenceGroup.findPreference(KEY_REDEYE_REDUCTION);
        ListPreference videoQuality = mPreferenceGroup.findPreference(KEY_VIDEO_QUALITY);
        ListPreference audioEncoder = mPreferenceGroup.findPreference(KEY_AUDIO_ENCODER);
        ListPreference noiseReduction = mPreferenceGroup.findPreference(KEY_NOISE_REDUCTION);
        ListPreference faceDetection = mPreferenceGroup.findPreference(KEY_FACE_DETECTION);
        ListPreference instantAec = mPreferenceGroup.findPreference(KEY_INSTANT_AEC);
        ListPreference saturationLevel = mPreferenceGroup.findPreference(KEY_SATURATION_LEVEL);
        ListPreference antiBandingLevel = mPreferenceGroup.findPreference(KEY_ANTI_BANDING_LEVEL);
        ListPreference stats_visualizer = mPreferenceGroup.findPreference(KEY_STATS_VISUALIZER_VALUE);
        ListPreference hdr = mPreferenceGroup.findPreference(KEY_HDR);
        ListPreference zoom = mPreferenceGroup.findPreference(KEY_ZOOM);
        ListPreference qcfa = mPreferenceGroup.findPreference(KEY_QCFA);
        ListPreference bsgc = mPreferenceGroup.findPreference(KEY_BSGC_DETECTION);
        ListPreference fsMode = mPreferenceGroup.findPreference(KEY_SENSOR_MODE_FS2_VALUE);

        if (whiteBalance != null) {
            if (filterUnsupportedOptions(whiteBalance, getSupportedWhiteBalanceModes(cameraId))) {
                mFilteredKeys.add(whiteBalance.getKey());
            }
        }

        if (flashMode != null) {
            if (!isFlashAvailable(mCameraId)) {
                removePreference(mPreferenceGroup, KEY_FLASH_MODE);
                removePreference(mPreferenceGroup, KEY_VIDEO_FLASH_MODE);
                mFilteredKeys.add(flashMode.getKey());
            }
            // Front Camera does not support video
            if (mCameraId == CaptureModule.FRONT_ID) {
                removePreference(mPreferenceGroup, KEY_VIDEO_FLASH_MODE);
            }
        }

        if (bsgc != null) {
            if (!isBsgcAvailable(mCameraId)) {
                removePreference(mPreferenceGroup, KEY_BSGC_DETECTION);
                mFilteredKeys.add(bsgc.getKey());
            }
        }

        if (colorEffect != null) {
            if (filterUnsupportedOptions(colorEffect, getSupportedColorEffects(cameraId))) {
                mFilteredKeys.add(colorEffect.getKey());
            }
        }

        if (instantAec != null) {
            if (filterUnsupportedOptions(instantAec,
                    getSupportedInstantAecAvailableModes(cameraId))) {
                mFilteredKeys.add(instantAec.getKey());
            }
        }

        if (saturationLevel != null) {
            if (filterUnsupportedOptions(saturationLevel,
                    getSupportedSaturationLevelAvailableModes(cameraId))) {
                mFilteredKeys.add(saturationLevel.getKey());
            }
        }

        if (antiBandingLevel != null) {
            if (filterUnsupportedOptions(antiBandingLevel,
                    getSupportedAntiBandingLevelAvailableModes(cameraId))) {
                mFilteredKeys.add(antiBandingLevel.getKey());
            }
        }

        if (hdr != null){
            if (filterUnsupportedOptions(hdr,
                    getSupportedHdrAvailableModes(cameraId))) {
                mFilteredKeys.add(hdr.getKey());
            }
        }

        if (sceneMode != null) {
            if (filterUnsupportedOptions(sceneMode, getSupportedSceneModes(cameraId))) {
                mFilteredKeys.add(sceneMode.getKey());
            }
        }

        if ( sceneModeInstructional != null ) {
            if (filterUnsupportedOptions(sceneModeInstructional,
                    getSupportedSceneModes(cameraId)) ){
                mFilteredKeys.add(sceneModeInstructional.getKey());
            }
        }

        if (cameraIdPref != null) buildCameraId();

        if (pictureSize != null) {
            CameraSettings.formatPictureSizes(pictureSize,
                    getSupportedPictureSizeList(cameraId), mContext);
            CameraSettings.resetIfInvalid(pictureSize);
        }

        if (exposure != null) buildExposureCompensation(cameraId);

        if (iso != null) {
            if (filterUnsupportedOptions(iso, getSupportedIso(cameraId))) {
                mFilteredKeys.add(iso.getKey());
            }
        }

        if (videoQuality != null) {
            if (filterUnsupportedOptions(videoQuality,
                    getSupportedVideoSize(cameraId))) {
                mFilteredKeys.add(videoQuality.getKey());
            }
        }

        if (!mIsMonoCameraPresent) {
            if (clearsight != null) removePreference(mPreferenceGroup, KEY_CLEARSIGHT);
            if (monoPreview != null) removePreference(mPreferenceGroup, KEY_MONO_PREVIEW);
            if (monoOnly != null) removePreference(mPreferenceGroup, KEY_MONO_ONLY);
            if (mpo != null) removePreference(mPreferenceGroup, KEY_MPO);
        }

        if (redeyeReduction != null) {
            if (filterUnsupportedOptions(redeyeReduction, getSupportedRedeyeReduction(cameraId))) {
                mFilteredKeys.add(redeyeReduction.getKey());
            }
        }

        if (audioEncoder != null) {
            if (filterUnsupportedOptions(audioEncoder,
                    getSupportedAudioEncoders(audioEncoder.getEntryValues()))) {
                mFilteredKeys.add(audioEncoder.getKey());
            }
        }

        if (noiseReduction != null) {
            if (filterUnsupportedOptions(noiseReduction,
                    getSupportedNoiseReductionModes(cameraId))) {
                mFilteredKeys.add(noiseReduction.getKey());
            }
        }

        if (faceDetection != null) {
            if (!isFaceDetectionSupported(cameraId)) {
                removePreference(mPreferenceGroup, KEY_FACE_DETECTION);
            }
        }

        // filter dynamic lists.
        // These list can be changed run-time
        filterHFROptions();
        filterVideoEncoderOptions();
        filterVideoEncoderProfileOptions();

        if (!mIsFrontCameraPresent || !isFacingFront(mCameraId)) {
            removePreference(mPreferenceGroup, KEY_SELFIE_FLASH);
            removePreference(mPreferenceGroup, KEY_SELFIEMIRROR);
        }

        if ( zoom != null ) {
            if (filterUnsupportedOptions(zoom,
                    getSupportedZoomLevel(cameraId))) {
                mFilteredKeys.add(zoom.getKey());
            }
        }

        if (fsMode != null) {
            if (!isFastShutterModeSupported(cameraId)) {
                removePreference(mPreferenceGroup, KEY_SENSOR_MODE_FS2_VALUE);
            }
        }

    }

    private void runTimeUpdateDependencyOptions(ListPreference pref) {
        // update the supported list
        if (pref.getKey().equals(KEY_VIDEO_QUALITY)) {
            filterHFROptions();
            filterVideoEncoderOptions();
        } else if (pref.getKey().equals(KEY_SCENE_MODE)) {
            filterChromaflashPictureSizeOptions();
        } else if ( pref.getKey().equals(KEY_VIDEO_ENCODER) ) {
            filterVideoEncoderProfileOptions();
        } else if (pref.getKey().equals(KEY_PICTURE_FORMAT)) {
            filterHeifSizeOptions();
        }
    }

    private void buildExposureCompensation(int cameraId) {
        Range<Integer> range = mCharacteristics.get(cameraId).get(CameraCharacteristics
                .CONTROL_AE_COMPENSATION_RANGE);
        int max = range.getUpper();
        int min = range.getLower();
        if (min == 0 && max == 0) {
            removePreference(mPreferenceGroup, KEY_EXPOSURE);
            return;
        }
        ListPreference pref = mPreferenceGroup.findPreference(KEY_EXPOSURE);
        Rational rational = mCharacteristics.get(cameraId).get(CameraCharacteristics
                .CONTROL_AE_COMPENSATION_STEP);
        double step = rational.doubleValue();
        int increment = 1;
        while ((max - min) / increment > 10) {
            increment++;
        }
        int start = min;
        if (start < 0) {
            while (Math.abs(start) % increment != 0) {
                start++;
            }
        }
        int size = 0;
        for (int i = start; i <= max; i += increment) size++;
        CharSequence entries[] = new CharSequence[size];
        CharSequence entryValues[] = new CharSequence[size];
        int count = 0;
        for (int i = start; i <= max; i += increment, count++) {
            entryValues[count] = Integer.toString(i);
            StringBuilder builder = new StringBuilder();
            if (i > 0) builder.append('+');
            DecimalFormat format = new DecimalFormat("#.##");
            entries[count] = builder.append(format.format(i * step)).toString();
        }
        pref.setEntries(entries);
        pref.setEntryValues(entryValues);
    }

    public CharSequence[] getExposureCompensationEntries() {
          ListPreference pref = mPreferenceGroup.findPreference(KEY_EXPOSURE);
        if (pref == null) return null;
        return pref.getEntries();
    }

    public CharSequence[] getExposureCompensationEntryValues() {
        ListPreference pref = mPreferenceGroup.findPreference(KEY_EXPOSURE);
        if (pref == null) return null;
        return pref.getEntryValues();
    }

    public int[] getWBColorTemperatureRangeValues(int cameraId) {
        int[] wbRange = null;
        try {
            wbRange =  mCharacteristics.get(cameraId).get(CaptureModule.WB_COLOR_TEMPERATURE_RANGE);
            if (wbRange == null) {
                Log.w(TAG, "Supported exposure range get null.");
                return null;
            }
        } catch(NullPointerException e) {
            Log.w(TAG, "Supported exposure range modes is null.");
        } catch(IllegalArgumentException e) {
            Log.w(TAG, "Supported exposure range modes occur IllegalArgumentException.");
        }
        return wbRange;
    }

    public float[] getWBGainsRangeValues(int cameraId) {
        float[] rgbRange = null;
        try {
            rgbRange =  mCharacteristics.get(cameraId).get(CaptureModule.WB_RGB_GAINS_RANGE);
            if (rgbRange == null) {
                Log.w(TAG, "Supported gains range get null.");
                return null;
            }
        } catch(NullPointerException e) {
            Log.w(TAG, "Supported gains range modes is null.");
        } catch(IllegalArgumentException e) {
            Log.w(TAG, "Supported gains range modes occur IllegalArgumentException.");
        }
        return rgbRange;
    }

    public long[] getExposureRangeValues(int cameraId) {
        long[] exposureRange = null;
        try {
            exposureRange =  mCharacteristics.get(cameraId).get(
                    CaptureModule.EXPOSURE_RANGE);
            if (exposureRange == null) {
                Log.w(TAG, "get exposure range modes is null.");
                return null;
            }
        } catch(NullPointerException e) {
            Log.w(TAG, "Supported exposure range modes is null.");
        } catch(IllegalArgumentException e) {
            Log.w(TAG, "IllegalArgumentException Supported exposure range modes is null.");
        }
        return exposureRange;
    }

    public int[] getIsoRangeValues(int cameraId) {
        Range<Integer> range = null;
        int[] result = new int[2];
        try {
            range = mCharacteristics.get(cameraId).get(CameraCharacteristics
                    .SENSOR_INFO_SENSITIVITY_RANGE);
            if (range == null) {
                return null;
            }
            result[0] = range.getLower();
            result[1] = range.getUpper();
        } catch(NullPointerException e) {
            Log.w(TAG, "Supported iso range is null.");
        } catch(IllegalArgumentException e) {
            Log.w(TAG, "IllegalArgumentException Supported iso range is null.");
        }
        return result;
    }

    private void buildCameraId() {
        int numOfCameras = mCharacteristics.size();
        CharSequence[] fullEntryValues = new CharSequence[numOfCameras + 1];
        CharSequence[] fullEntries = new CharSequence[numOfCameras + 1];
        for(int i = 0; i < numOfCameras ; i++) {
            int facing = mCharacteristics.get(i).get(CameraCharacteristics.LENS_FACING);
            String facingString =
                    facing == CameraCharacteristics.LENS_FACING_FRONT? "front" : "back";
            fullEntries[i] = "camera " + i +" facing:"+facingString;
            fullEntryValues[i] = "" + i;
            Log.d(TAG,"add "+fullEntries[i]+"="+ fullEntryValues[i]);
        }
        fullEntries[numOfCameras] = "disable";
        fullEntryValues[numOfCameras] = "" + -1;
        ListPreference switchPref = mPreferenceGroup.findPreference(KEY_SWITCH_CAMERA);
        switchPref.setEntries(fullEntries);
        switchPref.setEntryValues(fullEntryValues);
        if (!mIsFrontCameraPresent) {
            Log.d(TAG,"no front camera,remove camera id pref");
            removePreference(mPreferenceGroup, KEY_CAMERA_ID);
            return;
        }
        CharSequence[] entryValues = new CharSequence[numOfCameras];
        CharSequence[] entries = new CharSequence[numOfCameras];
        //TODO: Modify this after bayer/mono/front/back determination is done
        entryValues[0] = "" + CaptureModule.BAYER_ID;
        entries[0] = "BACK";
        if (mIsFrontCameraPresent && (numOfCameras > 1)) {
            entryValues[1] = "" + CaptureModule.FRONT_ID;
            entries[1] = "FRONT";
        }
        ListPreference cameraIdPref = mPreferenceGroup.findPreference(KEY_CAMERA_ID);
        cameraIdPref.setEntryValues(entryValues);
        cameraIdPref.setEntries(entries);
    }

    private void filterVideoEncoderOptions() {
        ListPreference videoEncoder = mPreferenceGroup.findPreference(KEY_VIDEO_ENCODER);

        if (videoEncoder != null) {
            videoEncoder.reloadInitialEntriesAndEntryValues();
            if (filterUnsupportedOptions(videoEncoder,
                    getSupportedVideoEncoders())) {
                mFilteredKeys.add(videoEncoder.getKey());
            }
        }
    }

    private boolean getEISEnabled() {
        boolean result = false;
        ListPreference disPref = mPreferenceGroup.findPreference(KEY_DIS);
        ListPreference eisPref = mPreferenceGroup.findPreference(KEY_EIS_VALUE);
        if (disPref != null && eisPref != null) {
            result = ("on".equals(disPref.getValue()) &&
                    !("disable".equals(eisPref.getValue())));
        }
        return result;
    }

    public void filterEISVideQualityOptions() {
        ListPreference videoQualityPref = mPreferenceGroup.findPreference(KEY_VIDEO_QUALITY);
        if (videoQualityPref == null) return;
        videoQualityPref.reloadInitialEntriesAndEntryValues();
        if (filterUnsupportedOptions(videoQualityPref, getSupportedVideoSize(
                getCurrentCameraId()))) {
            mFilteredKeys.add(videoQualityPref.getKey());
        }
    }

    private void filterChromaflashPictureSizeOptions() {
        String scene = getValue(SettingsManager.KEY_SCENE_MODE);
        ListPreference picturePref = mPreferenceGroup.findPreference(KEY_PICTURE_SIZE);
        if (picturePref == null) return;
        CameraSettings.formatPictureSizes(picturePref,
                getSupportedPictureSizeList(getCurrentCameraId()), mContext);
        CameraSettings.resetIfInvalid(picturePref);
        if (Integer.parseInt(scene) == SCENE_MODE_CHROMAFLASH_INT) {
            if (filterUnsupportedOptions(picturePref, getSupportedChromaFlashPictureSize())) {
                mFilteredKeys.add(picturePref.getKey());
            }
            // if picture size is setted the CIF/QVGA, modify smallest supportted size .
            Size pictureSize = parseSize(getValue(KEY_PICTURE_SIZE));
            if (pictureSize.getWidth() <= 352 && pictureSize.getHeight() <= 288) {
                CharSequence[] entryValues = picturePref.getEntryValues();
                int size = entryValues.length;
                CharSequence smallerSize = entryValues[size -1];
                setValue(KEY_PICTURE_SIZE, smallerSize.toString());
            }
        } else {
            if (filterUnsupportedOptions(picturePref, getSupportedPictureSize(
                    getCurrentCameraId()))) {
                mFilteredKeys.add(picturePref.getKey());
            }
        }
    }

    private void filterHeifSizeOptions() {
        ListPreference picturePref = mPreferenceGroup.findPreference(KEY_PICTURE_SIZE);
        ListPreference videoQualityPref = mPreferenceGroup.findPreference(KEY_VIDEO_QUALITY);
        if (filterUnsupportedOptions(picturePref, getSupportedPictureSize(
                getCurrentCameraId()))) {
            mFilteredKeys.add(picturePref.getKey());
        }
        if (filterUnsupportedOptions(videoQualityPref, getSupportedVideoSize(
                getCurrentCameraId()))) {
            mFilteredKeys.add(videoQualityPref.getKey());
        }
    }

    private void filterHFROptions() {
        ListPreference hfrPref = mPreferenceGroup.findPreference(KEY_VIDEO_HIGH_FRAME_RATE);
        if (hfrPref != null) {
            hfrPref.reloadInitialEntriesAndEntryValues();
            if (filterUnsupportedOptions(hfrPref,
                    getSupportedHighFrameRate())) {
                mFilteredKeys.add(hfrPref.getKey());
            }
        }
    }

    private void filterVideoEncoderProfileOptions() {
        ListPreference videoEncoderProfilePref =
                mPreferenceGroup.findPreference(KEY_VIDEO_ENCODER_PROFILE);
        ListPreference videoEncoderPref = mPreferenceGroup.findPreference(KEY_VIDEO_ENCODER);
        if ( videoEncoderProfilePref != null && videoEncoderPref != null ) {
            String videoEncoder = videoEncoderPref.getValue();
            videoEncoderProfilePref.reloadInitialEntriesAndEntryValues();
            if ( filterUnsupportedOptions(videoEncoderProfilePref,
                    getSupportedVideoEncoderProfile(videoEncoder)) ) {
                mFilteredKeys.add(videoEncoderProfilePref.getKey());
            }
        }
    }

    private List<String> getSupportedChromaFlashPictureSize() {
        StreamConfigurationMap map = mCharacteristics.get(getCurrentCameraId()).get(
                CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
        Size[] sizes = map.getOutputSizes(ImageFormat.JPEG);
        List<String> res = new ArrayList<>();
        if (sizes != null) {
            for (int i = 0; i < sizes.length; i++) {
                if (sizes[i].getWidth() > 352 && sizes[i].getHeight() > 288) {
                    res.add(sizes[i].toString());
                }
            }
        }

        Size[] highResSizes = map.getHighResolutionOutputSizes(ImageFormat.JPEG);
        if (highResSizes != null) {
            for (int i = 0; i < highResSizes.length; i++) {
                if (sizes[i].getWidth() > 352 && sizes[i].getHeight() > 288) {
                    res.add(highResSizes[i].toString());
                }
            }
        }

        return res;
    }

    private List<String> getSupportedVideoEncoderForAutoTest(String videoSizeStr) {
        ArrayList<String> supported = new ArrayList<String>();
        ListPreference videoEncoder = mPreferenceGroup.findPreference(KEY_VIDEO_ENCODER);
        if (videoEncoder == null) return supported;

        if (videoEncoder != null) {
            String str = null;
            List<VideoEncoderCap> videoEncoders = EncoderCapabilities.getVideoEncoders();
            for (VideoEncoderCap vEncoder: videoEncoders) {
                str = SettingTranslation.getVideoEncoder(vEncoder.mCodec);
                if (str != null) {
                    if (videoSizeStr != null) {
                        Size videoSize = parseSize(videoSizeStr);
                        if (videoSize.getWidth() > vEncoder.mMaxFrameWidth ||
                                videoSize.getWidth() < vEncoder.mMinFrameWidth ||
                                videoSize.getHeight() > vEncoder.mMaxFrameHeight ||
                                videoSize.getHeight() < vEncoder.mMinFrameHeight) {
                            Log.e(TAG, "Codec = " + vEncoder.mCodec + ", capabilities: " +
                                    "mMinFrameWidth = " + vEncoder.mMinFrameWidth + " , " +
                                    "mMinFrameHeight = " + vEncoder.mMinFrameHeight + " , " +
                                    "mMaxFrameWidth = " + vEncoder.mMaxFrameWidth + " , " +
                                    "mMaxFrameHeight = " + vEncoder.mMaxFrameHeight);
                        } else {
                            supported.add(str);
                        }
                    }
                }
            }
        }
        return supported;
    }

    private List<String> getSupportedHFRForAutoTest(String videoSizeStr) {
        ArrayList<String> supported = new ArrayList<String>();
        ListPreference videoEncoder = mPreferenceGroup.findPreference(KEY_VIDEO_ENCODER);
        if (videoEncoder == null) return supported;
        int videoEncoderNum = SettingTranslation.getVideoEncoder(videoEncoder.getValue());
        VideoCapabilities videoCapabilities = null;
        boolean findVideoEncoder = false;
        if (videoSizeStr != null) {
            Size videoSize = parseSize(videoSizeStr);
            MediaCodecList allCodecs = new MediaCodecList(MediaCodecList.ALL_CODECS);
            for (MediaCodecInfo info : allCodecs.getCodecInfos()) {
                if (!info.isEncoder() || info.getName().contains("google")) continue;
                for (String type : info.getSupportedTypes()) {
                    if ((videoEncoderNum == MediaRecorder.VideoEncoder.MPEG_4_SP && type.equalsIgnoreCase(MediaFormat.MIMETYPE_VIDEO_MPEG4))
                            || (videoEncoderNum == MediaRecorder.VideoEncoder.H263 && type.equalsIgnoreCase(MediaFormat.MIMETYPE_VIDEO_H263))
                            || (videoEncoderNum == MediaRecorder.VideoEncoder.H264 && type.equalsIgnoreCase(MediaFormat.MIMETYPE_VIDEO_AVC))
                            || (videoEncoderNum == MediaRecorder.VideoEncoder.HEVC && type.equalsIgnoreCase(MediaFormat.MIMETYPE_VIDEO_HEVC))) {
                        CodecCapabilities codecCapabilities = info.getCapabilitiesForType(type);
                        videoCapabilities = codecCapabilities.getVideoCapabilities();
                        findVideoEncoder = true;
                        break;
                    }
                }
                if (findVideoEncoder) break;
            }

            try {
                Range[] range = getSupportedHighSpeedVideoFPSRange(mCameraId, videoSize);
                for (Range r : range) {
                    // To support HFR for both preview and recording,
                    // minmal FPS needs to be equal to maximum FPS
                    if ((int) r.getUpper() == (int) r.getLower()) {
                        if (videoCapabilities != null) {
                            if (videoCapabilities.areSizeAndRateSupported(
                                    videoSize.getWidth(), videoSize.getHeight(), (int) r.getUpper())) {
                                supported.add("hfr" + String.valueOf(r.getUpper()));
                                supported.add("hsr" + String.valueOf(r.getUpper()));
                            }
                        }
                    }
                }
            } catch (IllegalArgumentException ex) {
                Log.w(TAG, "HFR is not supported for this resolution " + ex);
            }
            if (mExtendedHFRSize != null && mExtendedHFRSize.length >= 3) {
                for (int i = 0; i < mExtendedHFRSize.length; i += 3) {
                    String item = "hfr" + mExtendedHFRSize[i + 2];
                    if (!supported.contains(item)
                            && videoSize.getWidth() <= mExtendedHFRSize[i]
                            && videoSize.getHeight() <= mExtendedHFRSize[i + 1]) {
                        if (videoCapabilities != null) {
                            if (videoCapabilities.areSizeAndRateSupported(
                                    videoSize.getWidth(), videoSize.getHeight(), mExtendedHFRSize[i + 2])) {
                                supported.add(item);
                                supported.add("hsr" + mExtendedHFRSize[i + 2]);
                            }
                        }
                    }
                }
            }
        }
        return supported;
    }

    private List<String> getSupportedHighFrameRate() {
        ArrayList<String> supported = new ArrayList<String>();
        supported.add("off");
        ListPreference videoQuality = mPreferenceGroup.findPreference(KEY_VIDEO_QUALITY);
        ListPreference videoEncoder = mPreferenceGroup.findPreference(KEY_VIDEO_ENCODER);
        if (videoQuality == null || videoEncoder == null) return supported;
        String videoSizeStr = videoQuality.getValue();
        int videoEncoderNum = SettingTranslation.getVideoEncoder(videoEncoder.getValue());
        VideoCapabilities videoCapabilities = null;
        boolean findVideoEncoder = false;
        if (videoSizeStr != null) {
            Size videoSize = parseSize(videoSizeStr);
            MediaCodecList allCodecs = new MediaCodecList(MediaCodecList.ALL_CODECS);
            for (MediaCodecInfo info : allCodecs.getCodecInfos()) {
                if (!info.isEncoder() || info.getName().contains("google")) continue;
                for (String type : info.getSupportedTypes()) {
                    if ((videoEncoderNum == MediaRecorder.VideoEncoder.MPEG_4_SP && type.equalsIgnoreCase(MediaFormat.MIMETYPE_VIDEO_MPEG4))
                            || (videoEncoderNum == MediaRecorder.VideoEncoder.H263 && type.equalsIgnoreCase(MediaFormat.MIMETYPE_VIDEO_H263))
                            || (videoEncoderNum == MediaRecorder.VideoEncoder.H264 && type.equalsIgnoreCase(MediaFormat.MIMETYPE_VIDEO_AVC))
                            || (videoEncoderNum == MediaRecorder.VideoEncoder.HEVC && type.equalsIgnoreCase(MediaFormat.MIMETYPE_VIDEO_HEVC))) {
                        CodecCapabilities codecCapabilities = info.getCapabilitiesForType(type);
                        videoCapabilities = codecCapabilities.getVideoCapabilities();
                        findVideoEncoder = true;
                        break;
                    }
                }
                if (findVideoEncoder) break;
            }

            try {
                Range[] range = getSupportedHighSpeedVideoFPSRange(mCameraId, videoSize);
                for (Range r : range) {
                    // To support HFR for both preview and recording,
                    // minmal FPS needs to be equal to maximum FPS
                    if ((int) r.getUpper() == (int) r.getLower()) {
                        if (videoCapabilities != null) {
                            if (videoCapabilities.areSizeAndRateSupported(
                                    videoSize.getWidth(), videoSize.getHeight(), (int) r.getUpper())) {
                                supported.add("hfr" + String.valueOf(r.getUpper()));
                                supported.add("hsr" + String.valueOf(r.getUpper()));
                            }
                        }
                    }
                }
            } catch (IllegalArgumentException ex) {
                Log.w(TAG, "HFR is not supported for this resolution " + ex);
            }
            if (mExtendedHFRSize != null && mExtendedHFRSize.length >= 3) {
                for (int i = 0; i < mExtendedHFRSize.length; i += 3) {
                    String item = "hfr" + mExtendedHFRSize[i + 2];
                    if (!supported.contains(item)
                            && videoSize.getWidth() <= mExtendedHFRSize[i]
                            && videoSize.getHeight() <= mExtendedHFRSize[i + 1]) {
                        if (videoCapabilities != null) {
                            if (videoCapabilities.areSizeAndRateSupported(
                                    videoSize.getWidth(), videoSize.getHeight(), mExtendedHFRSize[i + 2])) {
                                supported.add(item);
                                supported.add("hsr" + mExtendedHFRSize[i + 2]);
                            }
                        }
                    }
                }
            }
        }
        return supported;
    }

    private boolean removePreference(PreferenceGroup group, String key) {
        mFilteredKeys.add(key);
        for (int i = 0, n = group.size(); i < n; i++) {
            CameraPreference child = group.get(i);
            if (child instanceof PreferenceGroup) {
                if (removePreference((PreferenceGroup) child, key)) {
                    return true;
                }
            }
            if (child instanceof ListPreference &&
                    ((ListPreference) child).getKey().equals(key)) {
                group.removePreference(i);
                return true;
            }
        }
        return false;
    }

    public float getMaxZoom(int id) {
        return mCharacteristics.get(id).get(CameraCharacteristics
                .SCALER_AVAILABLE_MAX_DIGITAL_ZOOM);
    }

    public Rect getSensorActiveArraySize(int id) {
        return mCharacteristics.get(id).get(CameraCharacteristics
                .SENSOR_INFO_ACTIVE_ARRAY_SIZE);
    }

    public float getMaxZoom(List<Integer> ids) {
        float zoomMax = Float.MAX_VALUE;
        for (int id : ids) {
            zoomMax = Math.min(getMaxZoom(id), zoomMax);
        }
        return zoomMax;
    }

    public boolean isZoomSupported(int id) {
        return mCharacteristics.get(id).get(CameraCharacteristics
                .SCALER_AVAILABLE_MAX_DIGITAL_ZOOM) > 1f;
    }

    public boolean isAutoFocusRegionSupported(List<Integer> ids) {
        for (int id : ids) {
            if (!isAutoFocusRegionSupported(id))
                return false;
        }
        return true;
    }

    public boolean isAutoExposureRegionSupported(List<Integer> ids) {
        for (int id : ids) {
            if (!isAutoExposureRegionSupported(id))
                return false;
        }
        return true;
    }

    public boolean isZoomSupported(List<Integer> ids) {
        for (int id : ids) {
            if (!isZoomSupported(id))
                return false;
        }
        return true;
    }

    public boolean isZZHDRSupported() {
        int modes[] = null;
        try {
            modes = mCharacteristics.get(getCurrentCameraId())
                    .get(CaptureModule.support_video_hdr_modes);
        } catch (IllegalArgumentException e) {
            Log.w(TAG, "cannot find vendor tag: " +
                    CaptureModule.support_video_hdr_modes.toString());
        }
        return modes != null && modes.length > 1;
    }

    public boolean isAutoExposureRegionSupported(int id) {
        Integer maxAERegions = mCharacteristics.get(id).get(
                CameraCharacteristics.CONTROL_MAX_REGIONS_AE);
        return maxAERegions != null && maxAERegions > 0;
    }

    public boolean isAutoFocusRegionSupported(int id) {
        Integer maxAfRegions = mCharacteristics.get(id).get(
                CameraCharacteristics.CONTROL_MAX_REGIONS_AF);
        return maxAfRegions != null && maxAfRegions > 0;
    }

    public boolean isFixedFocus(int id) {
        Float focusDistance = mCharacteristics.get(id).get(CameraCharacteristics
                .LENS_INFO_MINIMUM_FOCUS_DISTANCE);
        if (focusDistance == null || focusDistance == 0) {
            return true;
        } else {
            return false;
        }
    }

    public boolean isFaceDetectionSupported(int id) {
        int[] faceDetection = mCharacteristics.get(id).get
                (CameraCharacteristics.STATISTICS_INFO_AVAILABLE_FACE_DETECT_MODES);
        if (faceDetection != null) {
            for (int value: faceDetection) {
                if (value == CameraMetadata.STATISTICS_FACE_DETECT_MODE_SIMPLE)
                    return true;
            }
        }
        return false;
    }

    public boolean isBsgcAvailable(int id) {
        boolean ret = false;
        try {
            if (mCharacteristics.size() > 0) {
                byte bsgc_available = mCharacteristics.get(id).get(CaptureModule.bsgcAvailable);
                ret = bsgc_available == 1;
            }
        } catch (IllegalArgumentException e) {
            e.printStackTrace();
        }
        return ret;
    }

    private boolean isFastShutterModeSupported(int id) {
        boolean result = false;
        try {
            byte fastModeSupport = mCharacteristics.get(id).get(CaptureModule.fs_mode_support);
            result = (fastModeSupport == 1);
        } catch (IllegalArgumentException e) {
            e.printStackTrace();
        }
        return result;
    }

    public boolean isFacingFront(int id) {
        int facing = mCharacteristics.get(id).get(CameraCharacteristics.LENS_FACING);
        return facing == CameraCharacteristics.LENS_FACING_FRONT;
    }

    public boolean isFlashSupported(int id) {
        return mCharacteristics.get(id).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) &&
                mValuesMap.get(KEY_FLASH_MODE) != null;
    }

    public boolean isZslSupported(int id) {
        boolean zslSupported = false;
        int [] capabilities = mCharacteristics.get(id)
                .get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES);
        for (int capability : capabilities) {
            if (capability == CameraCharacteristics
                    .REQUEST_AVAILABLE_CAPABILITIES_YUV_REPROCESSING ||
                    capability == CameraCharacteristics
                    .REQUEST_AVAILABLE_CAPABILITIES_PRIVATE_REPROCESSING) {
                zslSupported = true;
            }
        }
        Log.d(TAG, "isZslSupported=" + zslSupported);
        return zslSupported;
    }

    private List<Size> getSupportedPictureSizeList(int cameraId) {
        StreamConfigurationMap map = mCharacteristics.get(cameraId).get(
                CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
        Size[] sizes = map.getOutputSizes(ImageFormat.JPEG);
        List<Size> res = new ArrayList<>();
        if (sizes != null) {
            for (int i = 0; i < sizes.length; i++) {
                res.add(sizes[i]);
            }
        }

        Size[] highResSizes = map.getHighResolutionOutputSizes(ImageFormat.JPEG);
        if (highResSizes != null) {
            for (int i = 0; i < highResSizes.length; i++) {
                res.add(highResSizes[i]);
            }
        }

        return res;
    }

    private List<String> getSupportedPictureSize(int cameraId) {
        StreamConfigurationMap map = mCharacteristics.get(cameraId).get(
                CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
        Size[] sizes = map.getOutputSizes(ImageFormat.JPEG);
        List<String> res = new ArrayList<>();

        boolean isDeepportrait = getDeepportraitEnabled();
        boolean isHeifEnabled = getSavePictureFormat() == HEIF_FORMAT;

        if (getQcfaPrefEnabled() && getIsSupportedQcfa(cameraId)) {
            res.add(getSupportedQcfaDimension(cameraId));
        }

        VideoCapabilities heifCap = null;
        if (isHeifEnabled) {
            MediaCodecList list = new MediaCodecList(MediaCodecList.REGULAR_CODECS);
            for (MediaCodecInfo info :list.getCodecInfos()) {
                if (info.isEncoder() && info.getName().contains("heic")){
                    heifCap = info.getCapabilitiesForType(
                            MediaFormat.MIMETYPE_IMAGE_ANDROID_HEIC).getVideoCapabilities();
                    Log.d(TAG,"supported heif height range ="+heifCap.getSupportedHeights().toString() +
                            " width range ="+heifCap.getSupportedWidths().toString());
                }
            }
        }

        if (sizes != null) {
            for (int i = 0; i < sizes.length; i++) {
                if (isHeifEnabled && heifCap != null ){
                    if (!heifCap.getSupportedWidths().contains(sizes[i].getWidth()) ||
                        !heifCap.getSupportedHeights().contains(sizes[i].getHeight())){
                        continue;
                    }
                }
                if (isDeepportrait &&
                        (Math.min(sizes[i].getWidth(),sizes[i].getHeight()) < 720 ||
                        Math.max(sizes[i].getWidth(),sizes[i].getHeight()) <= 1024)) {
                    //some reslutions are not supported in deepportrait
                    continue;
                }
                res.add(sizes[i].toString());
            }
        }

        Size[] highResSizes = map.getHighResolutionOutputSizes(ImageFormat.JPEG);

        if (highResSizes != null) {
            for (int i = 0; i < highResSizes.length; i++) {
                if (isHeifEnabled && heifCap != null) {
                    if (!heifCap.getSupportedWidths().contains(highResSizes[i].getWidth()) ||
                            !heifCap.getSupportedHeights().contains(highResSizes[i].getHeight())){
                        continue;
                    }
                }
                res.add(highResSizes[i].toString());
            }
        }

        return res;
    }

    public Size[] getSupportedThumbnailSizes(int cameraId) {
        return mCharacteristics.get(cameraId).get(
                CameraCharacteristics.JPEG_AVAILABLE_THUMBNAIL_SIZES);
    }

    public Size[] getSupportedOutputSize(int cameraId, int format) {
        StreamConfigurationMap map = mCharacteristics.get(cameraId).get(
                CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
        return map.getOutputSizes(format);
    }

    public Size[] getSupportedOutputSize(int cameraId, Class cl) {
        StreamConfigurationMap map = mCharacteristics.get(cameraId).get(
                CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
        Size[] normal = map.getOutputSizes(cl);
        Size[] high = map.getHighResolutionOutputSizes(ImageFormat.PRIVATE);
        Size[] ret = new Size[normal.length+high.length];
        System.arraycopy(normal,0,ret,0,normal.length);
        System.arraycopy(high,0,ret,normal.length,high.length);
        return ret;
    }

    private List<String> getSupportedVideoSize(int cameraId) {
        StreamConfigurationMap map = mCharacteristics.get(cameraId).get(
                CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
        Size[] sizes = map.getOutputSizes(MediaRecorder.class);
        boolean eisEnabled = getEISEnabled();
        boolean isHeifEnabled = getSavePictureFormat() == HEIF_FORMAT;
        VideoCapabilities heifCap = null;
        if (isHeifEnabled) {
            MediaCodecList list = new MediaCodecList(MediaCodecList.REGULAR_CODECS);
            for (MediaCodecInfo info :list.getCodecInfos()) {
                if (info.isEncoder() && info.getName().contains("heic")){
                    heifCap = info.getCapabilitiesForType(
                            MediaFormat.MIMETYPE_IMAGE_ANDROID_HEIC).getVideoCapabilities();
                    Log.d(TAG,"supported heif height range ="+heifCap.getSupportedHeights().toString() +
                            " width range ="+heifCap.getSupportedWidths().toString());
                }
            }
        }
        List<String> res = new ArrayList<>();
        for (int i = 0; i < sizes.length; i++) {
            if (isHeifEnabled && heifCap != null ){
                if (!heifCap.getSupportedWidths().contains(sizes[i].getWidth()) ||
                        !heifCap.getSupportedHeights().contains(sizes[i].getHeight())){
                    continue;
                }
            }
            if (eisEnabled) {
                // eis didn`t support less than 720P
                if (Math.min(sizes[i].getWidth(),sizes[i].getHeight()) < 720 ||
                        Math.max(sizes[i].getWidth(),sizes[i].getHeight()) < 1280) {
                    continue;
                }
            }
            if (CameraSettings.VIDEO_QUALITY_TABLE.containsKey(sizes[i].toString())) {
                Integer profile = CameraSettings.VIDEO_QUALITY_TABLE.get(sizes[i].toString());
                if (profile != null) {
                    res.add(sizes[i].toString());
                }
            }
        }
        return res;
    }

    public Size[] getSupportedHighSpeedVideoSize(int cameraId) {
        StreamConfigurationMap map = mCharacteristics.get(cameraId).get(
                CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
        return map.getHighSpeedVideoSizes();
    }

    public Range[] getSupportedHighSpeedVideoFPSRange(int cameraId, Size videoSize) {
        StreamConfigurationMap map = mCharacteristics.get(cameraId).get(
                CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
        return map.getHighSpeedVideoFpsRangesFor(videoSize);
    }

    public int getHighSpeedVideoEncoderBitRate(CamcorderProfile profile, int targetRate,
                                               int captureRate) {
        long bitRate;
        String key = profile.videoFrameWidth+"x"+profile.videoFrameHeight+":"+captureRate;
        String resolutionFpsEncoder = key + ":" + profile.videoCodec;
        if (CameraSettings.VIDEO_ENCODER_BITRATE.containsKey(resolutionFpsEncoder)) {
            bitRate = CameraSettings.VIDEO_ENCODER_BITRATE.get(resolutionFpsEncoder);
        } else if (CameraSettings.VIDEO_ENCODER_BITRATE.containsKey(key)) {
            bitRate = CameraSettings.VIDEO_ENCODER_BITRATE.get(key);
        } else {
            Log.i(TAG, "No pre-defined bitrate for "+key);
            bitRate = (profile.videoBitRate * targetRate) / profile.videoFrameRate;
            return (int)bitRate;
        }
        if (targetRate != captureRate) { // HFR use case. Do scaling based on HSR bitrate
            bitRate = (bitRate * targetRate) / captureRate;
        }
        return (int)bitRate;
    }

    private List<String> getSupportedRedeyeReduction(int cameraId) {
        int[] flashModes = mCharacteristics.get(cameraId).get(CameraCharacteristics
                .CONTROL_AE_AVAILABLE_MODES);
        List<String> modes = new ArrayList<>();
        for (int i = 0; i < flashModes.length; i++) {
            if (flashModes[i] == CameraMetadata.CONTROL_AE_MODE_ON_AUTO_FLASH_REDEYE) {
                modes.add("off");
                modes.add("on");
                break;
            }
        }
        return modes;
    }

    public float getMinimumFocusDistance(int cameraId) {
        return mCharacteristics.get(cameraId)
                .get(CameraCharacteristics.LENS_INFO_MINIMUM_FOCUS_DISTANCE);
    }

    private List<String> getSupportedWhiteBalanceModes(int cameraId) {
        try {
            List<String> modes = new ArrayList<>();
            if (mCharacteristics.size() > 0) {
                int[] whiteBalanceModes = mCharacteristics.get(cameraId).get(CameraCharacteristics
                        .CONTROL_AWB_AVAILABLE_MODES);
                for (int mode : whiteBalanceModes) {
                    modes.add("" + mode);
                }
            }
            return modes;
        } catch (IndexOutOfBoundsException e) {
            return null;
        }
    }

    private List<String> getSupportedSceneModes(int cameraId) {
        int[] sceneModes = mCharacteristics.get(cameraId).get(CameraCharacteristics
                .CONTROL_AVAILABLE_SCENE_MODES);
        List<String> modes = new ArrayList<>();
        modes.add("0"); // need special case handle for auto scene mode
        if (mIsMonoCameraPresent) modes.add(SCENE_MODE_DUAL_STRING); // need special case handle for dual mode
        if (OptizoomFilter.isSupportedStatic()) modes.add(SCENE_MODE_OPTIZOOM_INT + "");
        if (UbifocusFilter.isSupportedStatic() && cameraId == CaptureModule.BAYER_ID) modes.add(SCENE_MODE_UBIFOCUS_INT + "");
        if (BestpictureFilter.isSupportedStatic() && cameraId == CaptureModule.BAYER_ID) modes.add(SCENE_MODE_BESTPICTURE_INT + "");
        if (PanoCaptureProcessView.isSupportedStatic() && cameraId == CaptureModule.BAYER_ID) modes.add(SCENE_MODE_PANORAMA_INT + "");
        if (ChromaflashFilter.isSupportedStatic() && cameraId == CaptureModule.BAYER_ID) modes.add(SCENE_MODE_CHROMAFLASH_INT + "");
        if (BlurbusterFilter.isSupportedStatic()) modes.add(SCENE_MODE_BLURBUSTER_INT + "");
        if (SharpshooterFilter.isSupportedStatic()) modes.add(SCENE_MODE_SHARPSHOOTER_INT + "");
        if (TrackingFocusFrameListener.isSupportedStatic()) modes.add(SCENE_MODE_TRACKINGFOCUS_INT + "");
        if (DeepZoomFilter.isSupportedStatic()) modes.add(SCENE_MODE_DEEPZOOM_INT + "");
        if (DeepPortraitFilter.isSupportedStatic()) modes.add(SCENE_MODE_DEEPPORTRAIT_INT+"");
        modes.add("" + SCENE_MODE_PROMODE_INT);
        for (int mode : sceneModes) {
            modes.add("" + mode);
        }
        return modes;
    }

    private List<String> getSupportedFlashModes(int cameraId) {
        int[] flashModes = mCharacteristics.get(cameraId).get(CameraCharacteristics
                .CONTROL_AE_AVAILABLE_MODES);
        List<String> modes = new ArrayList<>();
        for (int mode : flashModes) {
            modes.add("" + mode);
        }
        return modes;
    }

    private boolean isFlashAvailable(int cameraId) {
        if (mCharacteristics.size() > 0) {
            return mCharacteristics.get(cameraId).get(CameraCharacteristics.FLASH_INFO_AVAILABLE);
        } else {
            return false;
        }
    }

    public StreamConfigurationMap getStreamConfigurationMap(int cameraId){
        return mCharacteristics.get(cameraId)
                .get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
    }

    public List<String> getSupportedColorEffects(int cameraId) {
        List<String> modes = new ArrayList<>();
        if (mCharacteristics.size() > 0) {
            int[] flashModes = mCharacteristics.get(cameraId).get(CameraCharacteristics
                    .CONTROL_AVAILABLE_EFFECTS);
            for (int mode : flashModes) {
                modes.add("" + mode);
            }
        }
        return modes;
    }

    private List<String> getSupportedIso(int cameraId) {
        CameraCharacteristics cameraCharacteristics = mCharacteristics.get(cameraId);
        List<String> supportedIso = new ArrayList<>();
        supportedIso.add("auto");

        try {
            int[] modes = cameraCharacteristics.get(CaptureModule.ISO_AVAILABLE_MODES);
            if (modes != null) {
                for (int iso : modes) {
                    for (String key : KEY_ISO_INDEX.keySet()) {
                        if (KEY_ISO_INDEX.get(key).equals(iso)) {
                            supportedIso.add(key);
                        }
                    }
                }
            } else {
                Log.w(TAG, "Supported ISO range is null.");
            }
        } catch (NullPointerException e) {
            Log.w(TAG, "Supported ISO_AVAILABLE_MODES is null.");
        } catch (IllegalArgumentException e) {
            Log.w(TAG, "IllegalArgumentException Supported ISO_AVAILABLE_MODES is wrong.");

            Range<Integer> range = mCharacteristics.get(cameraId).get(
                    CameraCharacteristics.SENSOR_INFO_SENSITIVITY_RANGE);
            if (range != null) {
                int max = range.getUpper();
                int value = 50;
                while (value <= max) {
                    if (range.contains(value)) {
                        supportedIso.add(String.valueOf(value));
                    }
                    value += 50;
                }
            } else {
                Log.w(TAG, "Supported ISO range is null.");
            }
        }

        return supportedIso;
    }

    private boolean isCurrentVideoResolutionSupportedByEncoder(VideoEncoderCap encoderCap) {
        boolean supported = false;
        ListPreference videoQuality = mPreferenceGroup.findPreference(KEY_VIDEO_QUALITY);
        if (videoQuality == null) return supported;
        String videoSizeStr = videoQuality.getValue();
        if (videoSizeStr != null) {
            Size videoSize = parseSize(videoSizeStr);

            if (videoSize.getWidth() > encoderCap.mMaxFrameWidth ||
                    videoSize.getWidth() < encoderCap.mMinFrameWidth ||
                    videoSize.getHeight() > encoderCap.mMaxFrameHeight ||
                    videoSize.getHeight() < encoderCap.mMinFrameHeight) {
                Log.e(TAG, "Codec = " + encoderCap.mCodec + ", capabilities: " +
                        "mMinFrameWidth = " + encoderCap.mMinFrameWidth + " , " +
                        "mMinFrameHeight = " + encoderCap.mMinFrameHeight + " , " +
                        "mMaxFrameWidth = " + encoderCap.mMaxFrameWidth + " , " +
                        "mMaxFrameHeight = " + encoderCap.mMaxFrameHeight);
            } else {
                supported = true;
            }
        }
        return supported;
    }

    private List<String> getSupportedVideoEncoders() {
        ArrayList<String> supported = new ArrayList<String>();
        String str = null;
        List<VideoEncoderCap> videoEncoders = EncoderCapabilities.getVideoEncoders();
        for (VideoEncoderCap videoEncoder: videoEncoders) {
            str = SettingTranslation.getVideoEncoder(videoEncoder.mCodec);
            if (str != null) {
                if (isCurrentVideoResolutionSupportedByEncoder(videoEncoder)) {
                    supported.add(str);
                }
            }
        }

        return supported;
    }

    private static List<String> getSupportedAudioEncoders(CharSequence[] strings) {
        ArrayList<String> supported = new ArrayList<>();
        for (CharSequence cs: strings) {
            String s = cs.toString();
            int value = SettingTranslation.getAudioEncoder(s);
            if (value != SettingTranslation.NOT_FOUND) supported.add(s);
        }
        return supported;
    }

    public List<String> getSupportedNoiseReductionModes(int cameraId) {
        int[] noiseReduction = mCharacteristics.get(cameraId).get(CameraCharacteristics
                .NOISE_REDUCTION_AVAILABLE_NOISE_REDUCTION_MODES);
        List<String> modes = new ArrayList<>();
        if (noiseReduction != null) {
            for (int mode : noiseReduction) {
                String str = SettingTranslation.getNoiseReduction(mode);
                if (str != null) modes.add(str);
            }
        }
        return modes;
    }

    private  List<String> getSupportedZoomLevel(int cameraId) {
        float maxZoom = mCharacteristics.get(cameraId).get(CameraCharacteristics
                .SCALER_AVAILABLE_MAX_DIGITAL_ZOOM);
        ArrayList<String> supported = new ArrayList<String>();
        for (int zoomLevel = 0; zoomLevel <= maxZoom; zoomLevel++) {
            supported.add(String.valueOf(zoomLevel));
        }
        return supported;
    }

    private void resetIfInvalid(ListPreference pref) {
        // Set the value to the first entry if it is invalid.
        String value = pref.getValue();
        if (pref.findIndexOfValue(value) == -1) {
            pref.setValueIndex(0);
        }
    }

    private boolean filterSimilarPictureSize(PreferenceGroup group,
                                                    ListPreference pref) {
        pref.filterDuplicated();
        if (pref.getEntries().length <= 1) {
            removePreference(group, pref.getKey());
            return true;
        }
        resetIfInvalid(pref);
        return false;
    }

    public List<String> getSupportedInstantAecAvailableModes(int cameraId) {
        List<String> modes = new ArrayList<>();

        try {
            if (mCharacteristics.size() > 0) {
                int[] instantAecAvailableModes = mCharacteristics.get(cameraId).get(
                        CaptureModule.InstantAecAvailableModes);
                if (instantAecAvailableModes == null) {
                    return null;
                }
                for (int i : instantAecAvailableModes) {
                    modes.add("" + i);
                }
            }
        } catch(NullPointerException e) {
            Log.w(TAG, "Supported instant aec modes is null.");
        } catch(IllegalArgumentException e) {
            Log.w(TAG, "Supported instant aec modes is null.");
        }

        return  modes;
    }

    public boolean getQcfaPrefEnabled() {
        ListPreference qcfaPref = mPreferenceGroup.findPreference(KEY_QCFA);
        String qcfa = qcfaPref.getValue();
        if(qcfa != null && qcfa.equals("enable")) {
            return true;
        }
        return false;
    }

    public boolean getDeepportraitEnabled() {
        String dp = getValue(KEY_SCENE_MODE);
        if( dp!= null && Integer.valueOf(dp) == SCENE_MODE_DEEPPORTRAIT_INT) {
            return true;
        }
        return false;
    }

    public boolean getIsSupportedQcfa (int cameraId) {
        byte isSupportQcfa = 0;
        try {
            isSupportQcfa = mCharacteristics.get(cameraId).get(
                    CaptureModule.IS_SUPPORT_QCFA_SENSOR);
        }catch(Exception e) {
        }
        return isSupportQcfa == 1 ? true : false;
    }

    public String getSupportedQcfaDimension(int cameraId) {
        int[] qcfaDimension = mCharacteristics.get(cameraId).get(
                CaptureModule.QCFA_SUPPORT_DIMENSION);
        if (qcfaDimension == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < qcfaDimension.length; i ++) {
            sb.append(qcfaDimension[i]);
            if (i == 0) {
                sb.append("x");
            }
        }
        return  sb.toString();
    }

    public Size getQcfaSupportSize() {
        String qcfaSize = getSupportedQcfaDimension(mCameraId);
        if (qcfaSize != null) {
            return parseSize(getSupportedQcfaDimension(mCameraId));
        }
        return new Size(0, 0);
    }

    public List<String> getSupportedSaturationLevelAvailableModes(int cameraId) {
        int[] saturationLevelAvailableModes = {0,1,2,3,4,5,6,7,8,9,10};
        List<String> modes = new ArrayList<>();
        for (int i : saturationLevelAvailableModes) {
            modes.add(""+i);
        }
        return  modes;
    }

    public List<String> getSupportedAntiBandingLevelAvailableModes(int cameraId) {
        int[] antiBandingLevelAvailableModes = mCharacteristics.get(cameraId).get(
                CameraCharacteristics.CONTROL_AE_AVAILABLE_ANTIBANDING_MODES);
        List<String> modes = new ArrayList<>();
        for (int i : antiBandingLevelAvailableModes) {
            modes.add(""+i);
        }
        return  modes;
    }

    public List<String> getSupportedHdrAvailableModes(int cameraId) {
        String[] data = {"enable","disable"};
        List<String> modes = new ArrayList<>();
        for (String i : data) {
            modes.add(i);
        }
        return  modes;
    }

    public List<String> getSupportedVideoEncoderProfile(String videoEncoder) {
        List<String> profile = new ArrayList<>();
        profile.add("off");
        if ( VIDEO_ENCODER_PROFILE_TABLE.containsKey(videoEncoder) ) {
            profile.addAll(VIDEO_ENCODER_PROFILE_TABLE.get(videoEncoder));
        }
        return profile;
    }



    public boolean isCamera2HDRSupport(){
        String value = getValue(KEY_HDR);
        return value != null && value.equals("enable");
    }

    public int getSavePictureFormat() {
        String value = getValue(SettingsManager.KEY_PICTURE_FORMAT);
        if (value == null) return 0;
        return Integer.valueOf(value);
    }

    public boolean isZSLInHALEnabled(){
        String value = getValue(KEY_ZSL);
        String halZSLValue = mContext.getString(R.string.pref_camera2_zsl_entryvalue_hal_zsl);
        if ( value != null && value.equals(halZSLValue) ){
            return true;
        }else{
            return false;
        }
    }

    public boolean isZSLInAppEnabled(){
        String value = getValue(KEY_ZSL);
        String appZSLValue = mContext.getString(R.string.pref_camera2_zsl_entryvalue_app_zsl);
        if ( value != null && value.equals(appZSLValue)){
            return true;
        }else{
            return false;
        }
    }

    private boolean filterUnsupportedOptions(ListPreference pref, List<String> supported) {
        // Remove the preference if the parameter is not supported
        if (supported == null) {
            removePreference(mPreferenceGroup, pref.getKey());
            return true;
        }
        pref.filterUnsupported(supported);
        if (pref.getEntries().length <= 0) {
            removePreference(mPreferenceGroup, pref.getKey());
            return true;
        }

        resetIfInvalid(pref);
        return false;
    }

    public interface Listener {
        void onSettingsChanged(List<SettingState> settings);
    }

    static class Values {
        String value;
        String overriddenValue;

        Values(String value, String overriddenValue) {
            this.value = value;
            this.overriddenValue = overriddenValue;
        }
    }

    static class SettingState {
        String key;
        Values values;

        SettingState(String key, Values values) {
            this.key = key;
            this.values = values;
        }
    }

    public List<String> getDependentKeys(String key) {
        List<String> list = null;
        if (key.equals(KEY_VIDEO_QUALITY)) {
            list = new ArrayList<>();
            list.add(KEY_VIDEO_HIGH_FRAME_RATE);
        } else {
            String value = getValue(key);
            JSONObject dependencies = getDependencyList(key, value);
            if (dependencies != null) {
                list = new ArrayList<>();
                Iterator<String> it = dependencies.keys();
                while (it.hasNext()) {
                    list.add(it.next());
                }
            }
        }
        return list;
    }

    private JSONObject parseJson(String fileName) {
        String json;
        try {
            InputStream is = mContext.getAssets().open(fileName);
            int size = is.available();
            byte[] buffer = new byte[size];
            is.read(buffer);
            is.close();
            json = new String(buffer, "UTF-8");
            return new JSONObject(json);
        } catch (IOException ex) {
            ex.printStackTrace();
            return null;
        } catch (JSONException e) {
            e.printStackTrace();
            return null;
        }
    }

    private JSONObject getDependencyMapForKey(String key) {
        if (mDependency == null) return null;
        try {
            return mDependency.getJSONObject(key);
        } catch (JSONException e) {
            Log.w(TAG, "getDependencyMapForKey JSONException No value for:" + key);
            return null;
        }
    }

    private JSONObject getDependencyList(String key, String value) {
        JSONObject dependencyMap = getDependencyMapForKey(key);
        if (dependencyMap == null) return null;
        if (!dependencyMap.has(value)) value = "default";
        if (!dependencyMap.has(value)) return null;
        value = getDependencyKey(dependencyMap, value);
        try {
            return dependencyMap.getJSONObject(value);
        } catch (JSONException e) {
            Log.w(TAG, "getDependencyList JSONException No value for:" + key);
            return null;
        }
    }

    private String getDependencyKey(JSONObject dependencyMap, String value) {
        if (!dependencyMap.has(value)) value = "default";
        return value;
    }

    public void restoreSettings() {
        clearPerCameraPreferences();
        init();
    }

    private void clearPerCameraPreferences() {
        String[] preferencesNames = ComboPreferences.getSharedPreferencesNames(mContext);
        for ( String name : preferencesNames ) {
            SharedPreferences.Editor editor =
                    mContext.getSharedPreferences(name, Context.MODE_PRIVATE).edit();
            editor.clear();
            editor.commit();
        }

        SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(mContext);
        boolean requestPermission = pref.getBoolean(CameraSettings.KEY_REQUEST_PERMISSION, false );
        SharedPreferences.Editor editor =
                PreferenceManager.getDefaultSharedPreferences(mContext).edit();
        editor.clear();
        editor.putBoolean(CameraSettings.KEY_REQUEST_PERMISSION, requestPermission);
        editor.commit();
    }

    public boolean isDeveloperEnabled() {
        SharedPreferences sp = mContext.getSharedPreferences(
                ComboPreferences.getGlobalSharedPreferencesName(mContext),
                Context.MODE_PRIVATE);
        return sp.getBoolean(SettingsManager.KEY_DEVELOPER_MENU, false);
    }

}
