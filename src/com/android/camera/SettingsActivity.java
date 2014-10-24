/*
 * Copyright (c) 2016, The Linux Foundation. All rights reserved.
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

package com.android.camera;

import android.app.ActionBar;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.ImageFormat;
import android.os.Bundle;
import android.preference.ListPreference;
import android.preference.MultiSelectListPreference;
import android.preference.Preference;
import android.preference.PreferenceCategory;
import android.preference.PreferenceGroup;
import android.preference.PreferenceActivity;
import android.preference.PreferenceManager;
import android.preference.PreferenceScreen;
import android.preference.SwitchPreference;
import android.view.Window;
import android.view.WindowManager;
import android.util.Log;
import android.util.Size;
import android.widget.Toast;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.EditText;
import android.text.InputType;

import org.codeaurora.snapcam.R;
import com.android.camera.util.CameraUtil;
import com.android.camera.ui.RotateTextToast;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Arrays;

public class SettingsActivity extends PreferenceActivity {
    private static final String TAG = "SettingsActivity";
    private SettingsManager mSettingsManager;
    private SharedPreferences mSharedPreferences;
    private SharedPreferences mLocalSharedPref;
    private boolean mDeveloperMenuEnabled;
    private int privateCounter = 0;
    private final int DEVELOPER_MENU_TOUCH_COUNT = 10;

    private SharedPreferences.OnSharedPreferenceChangeListener mSharedPreferenceChangeListener
            = new SharedPreferences.OnSharedPreferenceChangeListener() {
        @Override
        public void onSharedPreferenceChanged(SharedPreferences sharedPreferences,
                                              String key) {
            Preference p = findPreference(key);
            if (p == null) return;
            String value;
            if (p instanceof SwitchPreference) {
                boolean checked = ((SwitchPreference) p).isChecked();
                value = checked ? "on" : "off";
                mSettingsManager.setValue(key, value);
            } else if (p instanceof ListPreference){
                value = ((ListPreference) p).getValue();
                mSettingsManager.setValue(key, value);
            } else if (p instanceof MultiSelectListPreference) {
                Set<String> valueSet = ((MultiSelectListPreference)p).getValues();
                mSettingsManager.setValue(key,valueSet);
            }
            if (key.equals(SettingsManager.KEY_VIDEO_QUALITY)) {
                updatePreference(SettingsManager.KEY_VIDEO_HIGH_FRAME_RATE);
                updatePreference(SettingsManager.KEY_VIDEO_ENCODER);
            }else if ( key.equals(SettingsManager.KEY_VIDEO_ENCODER) ) {
                updatePreference(SettingsManager.KEY_VIDEO_ENCODER_PROFILE);
            }
            List<String> list = mSettingsManager.getDependentKeys(key);
            if (list != null) {
                for (String dependentKey : list) {
                    updatePreferenceButton(dependentKey);
                }
            }
        }
    };

    private SettingsManager.Listener mListener = new SettingsManager.Listener(){
        @Override
        public void onSettingsChanged(List<SettingsManager.SettingState> settings){
            Map<String, SettingsManager.Values> map = mSettingsManager.getValuesMap();
            for( SettingsManager.SettingState state : settings) {
                SettingsManager.Values values = map.get(state.key);
                boolean enabled = values.overriddenValue == null;
                Preference pref = findPreference(state.key);
                if ( pref == null ) return;

                pref.setEnabled(enabled);

                if ( pref.getKey().equals(SettingsManager.KEY_MANUAL_EXPOSURE) ) {
                    UpdateManualExposureSettings();
                }

                if ( pref.getKey().equals(SettingsManager.KEY_QCFA) ||
                        pref.getKey().equals(SettingsManager.KEY_PICTURE_FORMAT) ) {
                    mSettingsManager.updatePictureAndVideoSize();
                    updatePreference(SettingsManager.KEY_PICTURE_SIZE);
                    updatePreference(SettingsManager.KEY_VIDEO_QUALITY);
                }

                if ( pref.getKey().equals(SettingsManager.KEY_VIDEO_HDR_VALUE) ) {
                    // when enable the Video HDR, app will disable the AUTO HDR.
                    updateConflictOptionState(SettingsManager.KEY_AUTO_HDR, pref,
                            "Disable", "disable");
                }

                if (pref.getKey().equals(SettingsManager.KEY_VIDEO_HIGH_FRAME_RATE)) {
                    updateConflictOptionState(SettingsManager.KEY_VIDEO_TIME_LAPSE_FRAME_INTERVAL,
                            pref, "Off", "0");
                }

                if ( (pref.getKey().equals(SettingsManager.KEY_MANUAL_WB)) ) {
                    updateManualWBSettings();
                }
            }
        }
    };

    /**
     * This method is to enable or disable the option which is conflict with changed setting
     * @param conflictKey key you want to change after setting is changed
     * @param changedPref preference of setting which is just changed
     * @param checkedValue Judgement condition of enable or disable. It is Entry not EntryValue
     * @param targetValue EntryValue you want to set into conflictKey when you disable it
     */
    private void updateConflictOptionState(String conflictKey, Preference changedPref,
                                           String checkedValue, String targetValue) {
        ListPreference conflictPref = (ListPreference) findPreference(conflictKey);
        if (!changedPref.getSummary().equals(checkedValue)) {
            conflictPref.setValue(targetValue);
            mSettingsManager.setValue(conflictKey, targetValue);
            conflictPref.setEnabled(false);
        } else {
            conflictPref.setEnabled(true);
        }
    }

    private void UpdateManualExposureSettings() {
        //dismiss all popups first, because we need to show edit dialog
        int cameraId = mSettingsManager.getCurrentCameraId();
        final SharedPreferences pref = SettingsActivity.this.getSharedPreferences(
                ComboPreferences.getLocalSharedPreferencesName(SettingsActivity.this,
                        cameraId),
                Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = pref.edit();
        final AlertDialog.Builder alert = new AlertDialog.Builder(SettingsActivity.this);
        LinearLayout linear = new LinearLayout(SettingsActivity.this);
        linear.setOrientation(1);
        final TextView ISOtext = new TextView(SettingsActivity.this);
        final EditText ISOinput = new EditText(SettingsActivity.this);
        final TextView ExpTimeText = new TextView(SettingsActivity.this);
        final EditText ExpTimeInput = new EditText(SettingsActivity.this);
        ISOinput.setInputType(InputType.TYPE_CLASS_NUMBER);
        ExpTimeInput.setInputType(InputType.TYPE_CLASS_NUMBER);
        alert.setTitle("Manual Exposure Settings");
        alert.setNegativeButton("Cancel",new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog,int id) {
                dialog.cancel();
            }
        });
        String isoPriority = this.getString(
                R.string.pref_camera_manual_exp_value_ISO_priority);
        String expTimePriority = this.getString(
                R.string.pref_camera_manual_exp_value_exptime_priority);
        String userSetting = this.getString(
                R.string.pref_camera_manual_exp_value_user_setting);
        String gainsPriority = this.getString(
                R.string.pref_camera_manual_exp_value_gains_priority);
        String manualExposureMode = mSettingsManager.getValue(SettingsManager.KEY_MANUAL_EXPOSURE);
        String currentISO = pref.getString(SettingsManager.KEY_MANUAL_ISO_VALUE, "-1");
        long[] exposureRange = mSettingsManager.getExposureRangeValues(cameraId);

        int[] isoRange = mSettingsManager.getIsoRangeValues(cameraId);
        if (!currentISO.equals("-1")) {
            ISOtext.setText("Current ISO is " + currentISO);
        }
        String currentExpTime = pref.getString(SettingsManager.KEY_MANUAL_EXPOSURE_VALUE, "-1");
        if (!currentExpTime.equals("-1")) {
            ExpTimeText.setText("Current exposure time is " + currentExpTime);
        }
        Log.v(TAG, "manual Exposure Mode selected = " + manualExposureMode);
        if (manualExposureMode.equals(isoPriority)) {
            alert.setMessage("Enter ISO in the range of " + isoRange[0] + " to " + isoRange[1]);
            linear.addView(ISOinput);
            linear.addView(ISOtext);
            alert.setView(linear);
            alert.setPositiveButton("Ok",new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog,int id) {
                    int newISO = -1;
                    String iso = ISOinput.getText().toString();
                    Log.v(TAG, "string iso length " + iso.length() + ", iso :" + iso);
                    if (iso.length() > 0) {
                        newISO = Integer.parseInt(iso);
                    }
                    if (newISO <= isoRange[1] && newISO >= isoRange[0]) {
                        editor.putString(SettingsManager.KEY_MANUAL_ISO_VALUE, iso);
                        editor.apply();
                    } else {
                        RotateTextToast.makeText(SettingsActivity.this, "Invalid ISO",
                                Toast.LENGTH_SHORT).show();
                    }
                }
            });
            alert.show();
        } else if (manualExposureMode.equals(expTimePriority)) {
            if (exposureRange == null) {
                alert.setMessage("Get Exposure time range is NULL ");
            } else {
                alert.setMessage("Enter exposure time in the range of " + exposureRange[0]
                        + "ns to " + exposureRange[1] + "ns");
            }
            linear.addView(ExpTimeInput);
            linear.addView(ExpTimeText);
            alert.setView(linear);
            alert.setPositiveButton("Ok",new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog,int id) {
                    double newExpTime = -1;
                    String expTime = ExpTimeInput.getText().toString();
                    if (expTime.length() > 0) {
                        try {
                            newExpTime = Double.parseDouble(expTime);
                        } catch (NumberFormatException e) {
                            Log.w(TAG, "Input expTime " + expTime + " is invalid");
                            newExpTime = Double.parseDouble(expTime) + 1f;
                        }
                    }
                    if (exposureRange != null &&
                            newExpTime <= exposureRange[1] && newExpTime >= exposureRange[0]) {
                        editor.putString(SettingsManager.KEY_MANUAL_EXPOSURE_VALUE, expTime);
                        editor.apply();
                    } else {
                        RotateTextToast.makeText(SettingsActivity.this, "Invalid exposure time",
                                Toast.LENGTH_SHORT).show();
                    }
                }
            });
            alert.show();
        } else if (manualExposureMode.equals(userSetting)) {
            alert.setMessage("Full manual mode - Enter both ISO and Exposure Time");
            final TextView ISORangeText = new TextView(this);
            final TextView ExpTimeRangeText = new TextView(this);
            ISORangeText.setText("Enter ISO in the range of " + isoRange[0] + " to " + isoRange[1]);
            if (exposureRange == null) {
                ExpTimeRangeText.setText("Get Exposure time range is NULL ");
            } else {
                ExpTimeRangeText.setText("Enter exposure time in the range of " + exposureRange[0]
                        + "ns to " + exposureRange[1] + "ns");
            }
            linear.addView(ISORangeText);
            linear.addView(ISOinput);
            linear.addView(ISOtext);
            linear.addView(ExpTimeRangeText);
            linear.addView(ExpTimeInput);
            linear.addView(ExpTimeText);
            alert.setView(linear);
            alert.setPositiveButton("Ok",new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog,int id) {
                    int newISO = -1;
                    String iso = ISOinput.getText().toString();
                    Log.v(TAG, "string iso length " + iso.length() + ", iso :" + iso);
                    if (iso.length() > 0) {
                        newISO = Integer.parseInt(iso);
                    }
                    if (newISO <= isoRange[1] && newISO >= isoRange[0]) {
                        editor.putString(SettingsManager.KEY_MANUAL_ISO_VALUE, iso);
                        editor.apply();
                    } else {
                        RotateTextToast.makeText(SettingsActivity.this, "Invalid ISO",
                                Toast.LENGTH_SHORT).show();
                    }

                    double newExpTime = -1;
                    String expTime = ExpTimeInput.getText().toString();
                    if (expTime.length() > 0) {
                        try {
                            newExpTime = Double.parseDouble(expTime);
                        } catch (NumberFormatException e) {
                            Log.w(TAG, "Input expTime " + expTime + " is invalid");
                            newExpTime = Double.parseDouble(expTime) + 1f;
                        }
                    }
                    if (exposureRange != null &&
                            newExpTime <= exposureRange[1] && newExpTime >= exposureRange[0]) {
                        editor.putString(SettingsManager.KEY_MANUAL_EXPOSURE_VALUE, expTime);
                        editor.apply();
                    } else {
                        RotateTextToast.makeText(SettingsActivity.this, "Invalid exposure time",
                                Toast.LENGTH_SHORT).show();
                    }
                }
            });
            alert.show();
        } else if (manualExposureMode.equals(gainsPriority)){
            handleManualGainsPriority(linear, ISOtext, ExpTimeInput, pref);
        }
    }

    private void handleManualGainsPriority(final LinearLayout linear, final TextView gainsText,
        final EditText gainsInput, final SharedPreferences pref) {
        SharedPreferences.Editor editor = pref.edit();
        final AlertDialog.Builder alert = new AlertDialog.Builder(SettingsActivity.this);
        int cameraId = mSettingsManager.getCurrentCameraId();
        int[] isoRange = mSettingsManager.getIsoRangeValues(cameraId);
        float[] gainsRange = new float[2];
        gainsRange[0] = 1.0f;
        gainsRange[1] = (float) isoRange[1]/isoRange[0];
        float currentGains = pref.getFloat(SettingsManager.KEY_MANUAL_GAINS_VALUE, -1.0f);
        if (currentGains != -1.0f) {
            gainsText.setText(" Current Gains is " + currentGains);
        } else {
            gainsText.setText(" Please enter gains value ");
        }
        alert.setMessage("Enter gains in the range of " + gainsRange[0] + " to " + gainsRange[1]);
        linear.addView(gainsInput);
        linear.addView(gainsText);
        alert.setView(linear);
        alert.setPositiveButton("Ok", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {
                float newGain = -1;
                String gain = gainsInput.getText().toString();
                Log.v(TAG, "string gain length " + gain.length() + ", gain :" + gain);
                if (gain.length() > 0) {
                    newGain = Float.parseFloat(gain);
                }
                if (newGain <= gainsRange[1] && newGain >= gainsRange[0]) {
                    editor.putFloat(SettingsManager.KEY_MANUAL_GAINS_VALUE, newGain);
                    editor.apply();
                } else {
                    RotateTextToast.makeText(SettingsActivity.this, "Invalid GAINS",
                            Toast.LENGTH_SHORT).show();
                }
            }
        });
        alert.show();
    }

    private void showManualWBGainDialog(final LinearLayout linear,
                                        final AlertDialog.Builder alert) {
        SharedPreferences.Editor editor = mLocalSharedPref.edit();
        final TextView rGainTtext = new TextView(SettingsActivity.this);
        final TextView rGainValue = new TextView(SettingsActivity.this);
        final EditText rGainInput = new EditText(SettingsActivity.this);
        final TextView gGainTtext = new TextView(SettingsActivity.this);
        final TextView gGainValue = new TextView(SettingsActivity.this);
        final EditText gGainInput = new EditText(SettingsActivity.this);
        final TextView bGainTtext = new TextView(SettingsActivity.this);
        final TextView bGainValue = new TextView(SettingsActivity.this);
        final EditText bGainInput = new EditText(SettingsActivity.this);
        int floatType = InputType.TYPE_NUMBER_FLAG_DECIMAL | InputType.TYPE_CLASS_NUMBER;
        rGainInput.setInputType(floatType);
        gGainInput.setInputType(floatType);
        bGainInput.setInputType(floatType);

        float rGain = mLocalSharedPref.getFloat(SettingsManager.KEY_MANUAL_WB_R_GAIN, -1.0f);
        float gGain = mLocalSharedPref.getFloat(SettingsManager.KEY_MANUAL_WB_G_GAIN, -1.0f);
        float bGain = mLocalSharedPref.getFloat(SettingsManager.KEY_MANUAL_WB_B_GAIN, -1.0f);

        if (rGain == -1.0) {
            rGainValue.setText(" Current rGain is " );
        } else {
            rGainValue.setText(" Current rGain is " + rGain);
        }
        if (rGain == -1.0) {
            gGainValue.setText(" Current gGain is " );
        } else {
            gGainValue.setText(" Current gGain is " + gGain);
        }
        if (rGain == -1.0) {
            bGainValue.setText(" Current bGain is ");
        } else {
            bGainValue.setText(" Current bGain is " + bGain);
        }
        int cameraId = mSettingsManager.getCurrentCameraId();
        final float[] gainsRange = mSettingsManager.getWBGainsRangeValues(cameraId);
        //refresh camera parameters to get latest CCT value
        if (gainsRange == null) {
            alert.setMessage("Enter gains value in the range get is NULL ");
        } else {
            alert.setMessage("Enter gains value in the range of " + gainsRange[0]+ " to " + gainsRange[1]);
        }
        linear.addView(rGainTtext);
        linear.addView(rGainInput);
        linear.addView(rGainValue);
        linear.addView(gGainTtext);
        linear.addView(gGainInput);
        linear.addView(gGainValue);
        linear.addView(bGainTtext);
        linear.addView(bGainInput);
        linear.addView(bGainValue);
        alert.setView(linear);
        alert.setPositiveButton("Ok", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {
                float rGain = -1.0f;
                float gGain = -1.0f;
                float bGain = -1.0f;
                String rgainStr = rGainInput.getText().toString();
                String ggainStr = gGainInput.getText().toString();
                String bgainStr = bGainInput.getText().toString();
                if (rgainStr.length() > 0) {
                    rGain = Float.parseFloat(rgainStr);
                }
                if (ggainStr.length() > 0) {
                    gGain = Float.parseFloat(ggainStr);
                }
                if (bgainStr.length() > 0) {
                    bGain = Float.parseFloat(bgainStr);
                }
                if (gainsRange == null) {
                    RotateTextToast.makeText(SettingsActivity.this, "Gains Range is NULL, " +
                            "Invalid gains", Toast.LENGTH_SHORT).show();
                    return;
                }
                if (rGain <= gainsRange[1] && rGain >= gainsRange[0]) {
                    Log.v(TAG, "Setting rGain value : " + rGain);
                    editor.putFloat(SettingsManager.KEY_MANUAL_WB_R_GAIN, rGain);
                } else {
                    RotateTextToast.makeText(SettingsActivity.this, "Invalid rGain value:",
                            Toast.LENGTH_SHORT).show();
                }
                if (gGain <= gainsRange[1] && gGain >= gainsRange[0]) {
                    Log.v(TAG, "Setting gGain value : " + gGain);
                    editor.putFloat(SettingsManager.KEY_MANUAL_WB_G_GAIN, gGain);
                } else {
                    RotateTextToast.makeText(SettingsActivity.this, "Invalid gGain value:",
                            Toast.LENGTH_SHORT).show();
                }
                if (bGain <= gainsRange[1] && bGain >= gainsRange[0]) {
                    Log.v(TAG, "Setting bGain value : " + bGain);
                    editor.putFloat(SettingsManager.KEY_MANUAL_WB_B_GAIN, bGain);
                } else {
                    RotateTextToast.makeText(SettingsActivity.this, "Invalid bGain value:",
                            Toast.LENGTH_SHORT).show();
                }
                editor.apply();
            }
        });
        alert.show();
    }

    private void updateManualWBSettings() {
        int cameraId = mSettingsManager.getCurrentCameraId();
        SharedPreferences.Editor editor = mLocalSharedPref.edit();
        final AlertDialog.Builder alert = new AlertDialog.Builder(SettingsActivity.this);
        LinearLayout linear = new LinearLayout(SettingsActivity.this);
        linear.setOrientation(1);
        alert.setTitle("Manual White Balance Settings");
        alert.setNegativeButton("Cancel",new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog,int id) {
                dialog.cancel();
            }
        });

        String cctMode = this.getString(
                R.string.pref_camera_manual_wb_value_color_temperature);
        String rgbGainMode = this.getString(
                R.string.pref_camera_manual_wb_value_rbgb_gains);
        String currentWBTemp = mLocalSharedPref.getString(
                SettingsManager.KEY_MANUAL_WB_TEMPERATURE_VALUE, "-1");
        final String manualWBMode = mSettingsManager.getValue(SettingsManager.KEY_MANUAL_WB);
        Log.v(TAG, "manualWBMode selected = " + manualWBMode);
        final int[] wbRange = mSettingsManager.getWBColorTemperatureRangeValues(cameraId);
        if (manualWBMode.equals(cctMode)) {
            final TextView CCTtext = new TextView(SettingsActivity.this);
            final EditText CCTinput = new EditText(SettingsActivity.this);
            CCTinput.setInputType(InputType.TYPE_CLASS_NUMBER);

            //refresh camera parameters to get latest CCT value
            if (currentWBTemp.equals("-1")) {
                CCTtext.setText(" Current CCT is ");
            } else {
                CCTtext.setText(" Current CCT is " + currentWBTemp);
            }
            if (wbRange == null) {
                alert.setMessage("Enter CCT value is get NULL ");
            } else {
                alert.setMessage("Enter CCT value in the range of " + wbRange[0]+ " to " + wbRange[1]);
            }
            linear.addView(CCTinput);
            linear.addView(CCTtext);
            alert.setView(linear);
            alert.setPositiveButton("Ok",new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog,int id) {
                    int newCCT = -1;
                    String cct = CCTinput.getText().toString();
                    if (cct.length() > 0) {
                        newCCT = Integer.parseInt(cct);
                    }
                    if (wbRange == null) {
                        RotateTextToast.makeText(SettingsActivity.this, "CCT Range is NULL, " +
                                        "Invalid CCT", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    if (newCCT <= wbRange[1] && newCCT >= wbRange[0]) {
                        Log.v(TAG, "Setting CCT value : " + newCCT);
                        //0 corresponds to manual CCT mode
                        editor.putString(SettingsManager.KEY_MANUAL_WB_TEMPERATURE_VALUE, cct);
                        editor.apply();
                    } else {
                        RotateTextToast.makeText(SettingsActivity.this, "Invalid CCT",
                                Toast.LENGTH_SHORT).show();
                    }
                }
            });
            alert.show();
        } else if (manualWBMode.equals(rgbGainMode)) {
            showManualWBGainDialog(linear, alert);
        } else {
            // user select off, nothing to do.
        }
    }

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
        final boolean isSecureCamera = getIntent().getBooleanExtra(
                CameraUtil.KEY_IS_SECURE_CAMERA, false);
        if (isSecureCamera) {
            setShowInLockScreen();
        }
        mSettingsManager = SettingsManager.getInstance();
        if (mSettingsManager == null) {
            finish();
            return;
        }

        int cameraId = mSettingsManager.getCurrentCameraId();
        mLocalSharedPref = this.getSharedPreferences(
                ComboPreferences.getLocalSharedPreferencesName(this, cameraId),
                Context.MODE_PRIVATE);
        mSettingsManager.registerListener(mListener);
        addPreferencesFromResource(R.xml.setting_menu_preferences);
        mSharedPreferences = getPreferenceManager().getSharedPreferences();
        mDeveloperMenuEnabled = mSharedPreferences.getBoolean(SettingsManager.KEY_DEVELOPER_MENU, false);

        filterPreferences();
        initializePreferences();

        mSharedPreferences.registerOnSharedPreferenceChangeListener(mSharedPreferenceChangeListener);

        for (int i = 0; i < getPreferenceScreen().getPreferenceCount(); i++) {
            PreferenceCategory category = (PreferenceCategory) getPreferenceScreen().getPreference(i);
            for (int j = 0; j < category.getPreferenceCount(); j++) {
                Preference pref = category.getPreference(j);
                pref.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {

                    @Override
                    public boolean onPreferenceClick(Preference preference) {
                        if (!mDeveloperMenuEnabled) {
                            if (preference.getKey().equals("version_info")) {
                                privateCounter++;
                                if (privateCounter >= DEVELOPER_MENU_TOUCH_COUNT) {
                                    mDeveloperMenuEnabled = true;
                                    mSharedPreferences.edit().putBoolean(SettingsManager.KEY_DEVELOPER_MENU, true).apply();
                                    SharedPreferences sp = SettingsActivity.this.getSharedPreferences(
                                            ComboPreferences.getGlobalSharedPreferencesName(SettingsActivity.this),
                                            Context.MODE_PRIVATE);
                                    sp.edit().putBoolean(SettingsManager.KEY_DEVELOPER_MENU, true).apply();
                                    Toast.makeText(SettingsActivity.this, "Camera developer option is enabled now", Toast.LENGTH_SHORT).show();
                                    recreate();
                                }
                            } else {
                                privateCounter = 0;
                            }
                        }

                        if ( preference.getKey().equals(SettingsManager.KEY_RESTORE_DEFAULT) ) {
                            onRestoreDefaultSettingsClick();
                        }
                        return false;
                    }

                });
            }
        }

    }

    private void filterPreferences() {
        String[] categories = {"photo", "video", "general", "developer"};
        Set<String> set = mSettingsManager.getFilteredKeys();
        if (!mDeveloperMenuEnabled) {
            if (set != null) {
                set.add(SettingsManager.KEY_MONO_PREVIEW);
                set.add(SettingsManager.KEY_MONO_ONLY);
                set.add(SettingsManager.KEY_CLEARSIGHT);
            }

            PreferenceGroup developer = (PreferenceGroup) findPreference("developer");
            //Before restore settings,if current is not developer mode,the developer
            // preferenceGroup has been removed when enter camera by default .So duplicate remove
            // it will cause crash.
            if (developer != null) {
                PreferenceScreen parent = getPreferenceScreen();
                parent.removePreference(developer);
            }
        }

        CharSequence[] entries = mSettingsManager.getEntries(SettingsManager.KEY_SCENE_MODE);
        if (entries != null) {
            List<CharSequence> list = Arrays.asList(entries);
            if (mDeveloperMenuEnabled && list != null && !list.contains("HDR")){
                Preference p = findPreference("pref_camera2_hdr_key");
                if (p != null){
                    PreferenceGroup developer = (PreferenceGroup)findPreference("developer");
                    developer.removePreference(p);
                }
            }
        }

        if (set != null) {
            for (String key : set) {
                Preference p = findPreference(key);
                if (p == null) continue;

                for (int i = 0; i < categories.length; i++) {
                    PreferenceGroup group = (PreferenceGroup) findPreference(categories[i]);
                    if (group.removePreference(p)) break;
                }
            }
        }
    }

    private void initializePreferences() {
        updatePreference(SettingsManager.KEY_PICTURE_SIZE);
        updatePreference(SettingsManager.KEY_PICTURE_FORMAT);
        updatePreference(SettingsManager.KEY_VIDEO_QUALITY);
        updatePreference(SettingsManager.KEY_EXPOSURE);
        updatePreference(SettingsManager.KEY_VIDEO_HIGH_FRAME_RATE);
        updatePreference(SettingsManager.KEY_VIDEO_ENCODER);
        updatePreference(SettingsManager.KEY_ZOOM);
        updatePreference(SettingsManager.KEY_SWITCH_CAMERA);
        updateMultiPreference(SettingsManager.KEY_STATS_VISUALIZER_VALUE);
        updatePictureSizePreferenceButton();
        updateVideoHDRPreference();

        Map<String, SettingsManager.Values> map = mSettingsManager.getValuesMap();
        if (map == null) return;
        Set<Map.Entry<String, SettingsManager.Values>> set = map.entrySet();

        for (Map.Entry<String, SettingsManager.Values> entry : set) {
            String key = entry.getKey();
            Preference p = findPreference(key);
            if (p == null) continue;

            SettingsManager.Values values = entry.getValue();
            boolean disabled = values.overriddenValue != null;
            String value = disabled ? values.overriddenValue : values.value;
            if (p instanceof SwitchPreference) {
                ((SwitchPreference) p).setChecked(isOn(value));
            } else if (p instanceof ListPreference) {
                ListPreference pref = (ListPreference) p;
                pref.setValue(value);
                if (pref.getEntryValues().length == 1) {
                    pref.setEnabled(false);
                }
            }
            if (disabled) p.setEnabled(false);
        }

        // when enable deepzoom, disable the KEY_PICTURE_SIZE
        String scene = mSettingsManager.getValue(SettingsManager.KEY_SCENE_MODE);
        if (scene != null) {
            int mode = Integer.parseInt(scene);
            if (mode == SettingsManager.SCENE_MODE_DEEPZOOM_INT) {
                Preference p = findPreference(SettingsManager.KEY_PICTURE_SIZE);
                p.setEnabled(false);
            }
        }

        // when get RAW10 size is null, disable the KEY_SAVERAW
        int cameraId = mSettingsManager.getCurrentCameraId();
        Size[] rawSize = mSettingsManager.getSupportedOutputSize(cameraId,
                ImageFormat.RAW10);
        if (rawSize == null) {
            Preference p = findPreference(SettingsManager.KEY_SAVERAW);
            p.setEnabled(false);
        }

        try {
            String versionName = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
            int index = versionName.indexOf(' ');
            versionName = versionName.substring(0, index);
            findPreference("version_info").setSummary(versionName);
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
        }
    }

    private void updateVideoHDRPreference() {
        ListPreference pref = (ListPreference)findPreference(SettingsManager.KEY_VIDEO_HDR_VALUE);
        if (pref == null) {
            return;
        }
        pref.setEnabled(mSettingsManager.isZZHDRSupported());
    }

    private void updatePreferenceButton(String key) {
        Preference pref =  findPreference(key);
        if (pref != null ) {
            if( pref instanceof ListPreference) {
                ListPreference pref2 = (ListPreference) pref;
                if (pref2.getEntryValues().length == 1) {
                    pref2.setEnabled(false);
                } else {
                    pref2.setEnabled(true);
                }
            }else {
                pref.setEnabled(false);
            }
        }
    }

    private void updatePictureSizePreferenceButton() {
        Preference picturePref =  findPreference(SettingsManager.KEY_PICTURE_SIZE);
        String sceneMode = mSettingsManager.getValue(SettingsManager.KEY_SCENE_MODE);
        if ( sceneMode != null && picturePref != null ){
            int sceneModeInt = Integer.parseInt(sceneMode);
            picturePref.setEnabled(sceneModeInt != SettingsManager.SCENE_MODE_DUAL_INT);
        }
    }

    private void updatePreference(String key) {
        ListPreference pref = (ListPreference) findPreference(key);
        if (pref != null) {
            if (mSettingsManager.getEntries(key) != null) {
                pref.setEntries(mSettingsManager.getEntries(key));
                pref.setEntryValues(mSettingsManager.getEntryValues(key));
                int idx = mSettingsManager.getValueIndex(key);
                if (idx < 0 ) {
                    idx = 0;
                }
                pref.setValueIndex(idx);
            }
        }
    }

    private void updateMultiPreference(String key) {
        MultiSelectListPreference pref = (MultiSelectListPreference) findPreference(key);
        if (pref != null) {
            if (mSettingsManager.getEntries(key) != null) {
                pref.setEntries(mSettingsManager.getEntries(key));
                pref.setEntryValues(mSettingsManager.getEntryValues(key));
                String values = mSettingsManager.getValue(key);
                if (values != null) {
                    Set<String> valueSet = new HashSet<String>();
                    String[] splitValues = values.split(";");
                    for (String str : splitValues) {
                        valueSet.add(str);
                    }
                    pref.setValues(valueSet);
                }
            }
        }
    }

    private boolean isOn(String value) {
        return value.equals("on") || value.equals("enable");
    }

    @Override
    protected void onStop() {
        super.onStop();
        mSharedPreferences.unregisterOnSharedPreferenceChangeListener(mSharedPreferenceChangeListener);
        finish();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mSettingsManager.unregisterListener(mListener);
    }

    private void setShowInLockScreen() {
        // Change the window flags so that secure camera can show when locked
        Window win = getWindow();
        WindowManager.LayoutParams params = win.getAttributes();
        params.flags |= WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED;
        win.setAttributes(params);
    }
    private
    void onRestoreDefaultSettingsClick() {
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
        mSettingsManager.restoreSettings();
        filterPreferences();
        initializePreferences();
    }
}
