/*
 * Copyright (C) 2009 The Android Open Source Project
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

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.TypedArray;
import android.util.AttributeSet;
import android.util.Log;
import android.util.TypedValue;

import com.android.camera.util.CameraUtil;
import com.android.camera.util.UsageStatistics;
import org.codeaurora.snapcam.R;

/**
 * A type of <code>CameraPreference</code> whose number of possible values
 * is limited.
 */
public class ListPreference extends CameraPreference {
    private static final String TAG = "ListPreference";
    private final String mKey;
    private String mValue;
    private final CharSequence[] mDefaultValues;

    private CharSequence[] mEntries;
    private CharSequence[] mEntryValues;
    private CharSequence[] mInitialEntries;
    private CharSequence[] mInitialEntryValues;
    private CharSequence[] mDependencyList;
    private CharSequence[] mLabels;
    private boolean mLoaded = false;

    public ListPreference(Context context, AttributeSet attrs) {
        super(context, attrs);

        TypedArray a = context.obtainStyledAttributes(
                attrs, R.styleable.ListPreference, 0, 0);

        mKey = CameraUtil.checkNotNull(
                a.getString(R.styleable.ListPreference_key));

        // We allow the defaultValue attribute to be a string or an array of
        // strings. The reason we need multiple default values is that some
        // of them may be unsupported on a specific platform (for example,
        // continuous auto-focus). In that case the first supported value
        // in the array will be used.
        int attrDefaultValue = R.styleable.ListPreference_defaultValue;
        TypedValue tv = a.peekValue(attrDefaultValue);
        if (tv != null && tv.type == TypedValue.TYPE_REFERENCE) {
            mDefaultValues = a.getTextArray(attrDefaultValue);
        } else {
            mDefaultValues = new CharSequence[1];
            mDefaultValues[0] = a.getString(attrDefaultValue);
        }

        setEntries(a.getTextArray(R.styleable.ListPreference_entries));
        setEntryValues(a.getTextArray(
                R.styleable.ListPreference_entryValues));
        mInitialEntryValues = mEntryValues;
        mInitialEntries = mEntries;

        setLabels(a.getTextArray(
                R.styleable.ListPreference_labelList));
        setDependencyList(a.getTextArray(
                R.styleable.ListPreference_dependencyList));
        a.recycle();
    }

    public String getKey() {
        return mKey;
    }

    public CharSequence[] getEntries() {
        return mEntries;
    }

    public CharSequence[] getEntryValues() {
        return mEntryValues;
    }

    public CharSequence[] getLabels() {
        return mLabels;
    }

    public CharSequence[] getDependencyList() {
        return mDependencyList;
    }

    public void setEntries(CharSequence entries[]) {
        mEntries = entries == null ? new CharSequence[0] : entries;
    }

    public void setEntryValues(CharSequence values[]) {
        mEntryValues = values == null ? new CharSequence[0] : values;
    }

    public void setLabels(CharSequence labels[]) {
        mLabels = labels == null ? new CharSequence[0] : labels;
    }

    public void setDependencyList(CharSequence dependencyList[]) {
        mDependencyList = dependencyList == null ? new CharSequence[0] : dependencyList;
    }

    public String getValue() {
        if (!mLoaded) {
            mValue = getSharedPreferences().getString(mKey,
                    findSupportedDefaultValue());
            mLoaded = true;
        }
        return mValue;
    }

    public String getOffValue() {
        return mEntryValues[0].toString();
    }

    // Find the first value in mDefaultValues which is supported.
    private String findSupportedDefaultValue() {
        for (int i = 0; i < mDefaultValues.length; i++) {
            for (int j = 0; j < mEntryValues.length; j++) {
                // Note that mDefaultValues[i] may be null (if unspecified
                // in the xml file).
                if (mEntryValues[j].equals(mDefaultValues[i])) {
                    return mDefaultValues[i].toString();
                }
            }
        }
        return null;
    }

    public void setValue(String value) {
        if (findIndexOfValue(value) < 0) {
            value = findSupportedDefaultValue();
        }
        mValue = value;
        persistStringValue(value);
    }

    public void setMakeupSeekBarValue(String value) {
        mValue = value;
        persistStringValue(value);
    }

    public void setFromMultiValues(Set<String> set) {
        String value = "";
        for (String str : set) {
            value = value + str +";";
        }
        mValue = value;
        persistStringValue(value);
    }

    public void setValueIndex(int index) {
        setValue(mEntryValues[index].toString());
    }

    public int findIndexOfValue(String value) {
        for (int i = 0, n = mEntryValues.length; i < n; ++i) {
            if (CameraUtil.equals(mEntryValues[i], value)) return i;
        }

        String defaultValue = findSupportedDefaultValue();
        if (defaultValue != null) {
            for (int i = 0, n = mEntryValues.length; i < n; ++i) {
                if (CameraUtil.equals(mEntryValues[i], defaultValue)) return i;
            }
        }
        return -1;
    }

    public int getCurrentIndex() {
        return findIndexOfValue(getValue());
    }

    public String getEntry() {
        int index  = findIndexOfValue(getValue());
        if(index < 0) {
            return findSupportedDefaultValue();
        }
        return mEntries[index].toString();
    }

    public String getLabel() {
        int index = findIndexOfValue(getValue());
        if (index < 0) {
            return findSupportedDefaultValue();
        }
        return mLabels[index].toString();
    }

    protected void persistStringValue(String value) {
        SharedPreferences.Editor editor = getSharedPreferences().edit();
        editor.putString(mKey, value);
        editor.apply();
        UsageStatistics.onEvent("CameraSettingsChange", value, mKey);
    }

    @Override
    public void reloadValue() {
        this.mLoaded = false;
    }

    public void filterUnsupported(List<String> supported) {
        ArrayList<CharSequence> entries = new ArrayList<CharSequence>();
        ArrayList<CharSequence> entryValues = new ArrayList<CharSequence>();
        for (int i = 0, len = mEntryValues.length; i < len; i++) {
            if (i >= mEntries.length) break;
            if (supported.indexOf(mEntryValues[i].toString()) >= 0) {
                entries.add(mEntries[i]);
                entryValues.add(mEntryValues[i]);
            }
        }
        int size = entries.size();
        mEntries = entries.toArray(new CharSequence[size]);
        mEntryValues = entryValues.toArray(new CharSequence[size]);
    }

    public void filterDuplicated() {
        ArrayList<CharSequence> entries = new ArrayList<CharSequence>();
        ArrayList<CharSequence> entryValues = new ArrayList<CharSequence>();
        for (int i = 0, len = mEntryValues.length; i < len; i++) {
            if (!entries.contains(mEntries[i])) {
                entries.add(mEntries[i]);
                entryValues.add(mEntryValues[i]);
            }
        }
        int size = entries.size();
        mEntries = entries.toArray(new CharSequence[size]);
        mEntryValues = entryValues.toArray(new CharSequence[size]);
    }

    public void reloadInitialEntriesAndEntryValues() {
        mEntries = mInitialEntries;
        mEntryValues = mInitialEntryValues;
    }
    public void print() {
        Log.v(TAG, "Preference key=" + getKey() + ". value=" + getValue());
        for (int i = 0; i < mEntryValues.length; i++) {
            Log.v(TAG, "entryValues[" + i + "]=" + mEntryValues[i]);
        }
    }
}
