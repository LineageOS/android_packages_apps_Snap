/*
 * Copyright (C) 2017 The LineageOS Project
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

import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.provider.CalendarContract;
import android.provider.ContactsContract;
import android.text.TextUtils;
import android.widget.Toast;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import org.codeaurora.snapcam.R;

class ScannerIntentHelper {
    private static final String EVENT_SUMMARY = "SUMMARY:";
    private static final String EVENT_DESCRIPTION = "DESCRIPTION:";
    private static final String EVENT_START = "DTSTART:";
    private static final String EVENT_END = "DTEND:";
    private static final String EVENT_LOCATION = "LOCATION:";
    private static final String EVENT_DATE_FORMAT = "yyyyMMdd";

    private static final String SMS_URI = "smsto:";
    private static final String SMS_BODY = "sms_body";

    private static final String CONTACT_NAME = "N:";
    private static final String CONTACT_ORG = "ORG:";
    private static final String CONTACT_TITLE = "TITLE:";
    private static final String CONTACT_TEL = "TEL:";
    private static final String CONTACT_EMAIL = "EMAIL:";
    private static final String CONTACT_NOTE = "NOTE:";

    private static final String MEBKM_URL = "URL:";

    private static ScannerIntentHelper INSTANCE;

    private Intent mIntent;

    private String mScannedText;

    private ScannerIntentHelper() {
    }

    static ScannerIntentHelper getInstance() {
        if (INSTANCE == null) {
            INSTANCE = new ScannerIntentHelper();
        }

        return INSTANCE;
    }

    void run(Context context) {
        if (mIntent == null) {
            showScannedText(context);
        } else {
            runIntent(context);
        }
    }

    void reset() {
        mIntent = null;
        mScannedText = null;
    }

    boolean isValid() {
        return mIntent != null || !TextUtils.isEmpty(mScannedText);
    }

    void setUriIntent(String uri) {
        mIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(uri));
    }

    void setCalendarIntent(String text) {
        String[] data = text.split("\n");
        String summary = "";
        String description = "";
        String location = "";
        Date start = new Date();
        Date end = new Date();

        for (String item : data) {
            if (item.startsWith(EVENT_SUMMARY)) {
                summary = item.replace(EVENT_SUMMARY, "");
            } else if (item.startsWith(EVENT_DESCRIPTION)) {
                summary = item.replace(EVENT_DESCRIPTION, "");
            } else if (item.startsWith(EVENT_LOCATION)) {
                location = item.replace(EVENT_LOCATION, "");
            } else if (item.startsWith(EVENT_START)) {
                start = parseDate(item.replace(EVENT_START, ""));
            } else if (item.startsWith(EVENT_END)) {
                start = parseDate(item.replace(EVENT_END, ""));
            }
        }

        mIntent = new Intent(Intent.ACTION_INSERT, CalendarContract.Events.CONTENT_URI);
        mIntent.putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, start)
                .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, end)
                .putExtra(CalendarContract.Events.TITLE, summary)
                .putExtra(CalendarContract.Events.DESCRIPTION, description)
                .putExtra(CalendarContract.Events.EVENT_LOCATION, location);
    }

    void setSMSIntent(String text) {
        String[] data = text.split(":");

        mIntent = new Intent(Intent.ACTION_SENDTO, Uri.parse(SMS_URI + data[1]));
        if (data.length > 2) {
            mIntent.putExtra(SMS_BODY, data[2]);
        }
    }

    void setContactIntent(String text, boolean isVCard) {
        String[] data = text.split(isVCard ? "\n" : ";");
        String name = "";
        String org = "";
        String title = "";
        String telephone = "";
        String email = "";
        String notes = "";


        for (String item : data) {
            if (item.startsWith(CONTACT_NAME)) {
                name = item.replace(CONTACT_NAME, "");
            } else if (item.startsWith(CONTACT_ORG)) {
                org = item.replace(CONTACT_ORG, "");
            } else if (item.startsWith(CONTACT_TITLE)) {
                title = item.replace(CONTACT_TITLE, "");
            } else if (item.startsWith(CONTACT_TEL)) {
                telephone = item.replace(CONTACT_TEL, "");
            } else if (item.startsWith(CONTACT_EMAIL)) {
                email = item.replace(CONTACT_EMAIL, "");
            } else if (item.startsWith(CONTACT_NOTE)) {
                notes = item.replace(CONTACT_NOTE, "");
            }
        }

        mIntent = new Intent(ContactsContract.Intents.Insert.ACTION);
        mIntent.setType(ContactsContract.RawContacts.CONTENT_TYPE)
                .putExtra(ContactsContract.Intents.Insert.NAME, name)
                .putExtra(ContactsContract.Intents.Insert.COMPANY, org)
                .putExtra(ContactsContract.Intents.Insert.JOB_TITLE, title)
                .putExtra(ContactsContract.Intents.Insert.PHONE, telephone)
                .putExtra(ContactsContract.Intents.Insert.PHONE_TYPE,
                        ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE)
                .putExtra(ContactsContract.Intents.Insert.EMAIL, email)
                .putExtra(ContactsContract.Intents.Insert.EMAIL_TYPE,
                        ContactsContract.CommonDataKinds.Email.TYPE_WORK)
                .putExtra(ContactsContract.Intents.Insert.NOTES, notes);
    }

    void setMeBkmUrl(String text) {
        String[] data = text.split(":");

        for (String item : data) {
            if (!item.startsWith(MEBKM_URL)) {
                continue;
            }

            setUriIntent(item.replace(MEBKM_URL, "").replace("\\", "").replace(";", ""));
        }
    }

    void setText(String text) {
        mScannedText = text;
    }

    private Date parseDate(String item) {
        SimpleDateFormat format = new SimpleDateFormat(EVENT_DATE_FORMAT, Locale.getDefault());
        String formattedDate = item.split("T")[0];

        try {
            return format.parse(formattedDate);
        } catch (ParseException e) {
            return new Date();
        }
    }

    private void runIntent(Context context) {
        if (mIntent.resolveActivity(context.getPackageManager()) == null) {
            Toast.makeText(context, context.getString(R.string.quick_reader_no_activity_found),
                    Toast.LENGTH_LONG).show();
        } else {
            context.startActivity(mIntent);
        }
    }

    private void showScannedText(Context context) {
        new AlertDialog.Builder(context)
                .setTitle(R.string.quick_reader_scanned_text_title)
                .setMessage(mScannedText)
                .setNeutralButton(R.string.quick_reader_action_dismiss, null)
                .setPositiveButton(R.string.quick_reader_scanned_text_positive,
                        (dialog, i) -> copyScanned(context))
                .setNegativeButton(R.string.quick_reader_scanned_text_negative,
                        (dialog, i) -> shareScanned(context))
                .show();
    }

    private void copyScanned(Context context) {
        ClipboardManager manager = (ClipboardManager)
                context.getSystemService(Context.CLIPBOARD_SERVICE);
        if (manager != null) {
            manager.setPrimaryClip(ClipData.newPlainText("", mScannedText));
            Toast.makeText(context, context.getString(R.string.quick_reader_copied_message),
                    Toast.LENGTH_LONG).show();
        }
    }

    private void shareScanned(Context context) {
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType("text/plain");
        intent.putExtra(Intent.EXTRA_TEXT, mScannedText);
        context.startActivity(Intent.createChooser(intent,
                context.getString(R.string.quick_reader_share_title)));
    }
}