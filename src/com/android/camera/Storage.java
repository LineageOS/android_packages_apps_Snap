/*
 * Copyright (C) 2010 The Android Open Source Project
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

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

import android.annotation.TargetApi;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.location.Location;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.StatFs;
import android.provider.MediaStore.Images;
import android.provider.MediaStore.Images.ImageColumns;
import android.provider.MediaStore.MediaColumns;
import android.util.Log;

import com.android.camera.data.LocalData;
import com.android.camera.exif.ExifInterface;
import com.android.camera.util.ApiHelper;
import androidx.heifwriter.HeifWriter;
import android.graphics.ImageFormat;

public class Storage {
    private static final String TAG = "CameraStorage";

    public static final String DCIM =
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM).toString();

    public static final String DIRECTORY = DCIM + "/Camera";
    public static final String RAW_DIRECTORY = DCIM + "/Camera/raw";
    public static final String JPEG_POSTFIX = ".jpg";
    public static final String HEIF_POSTFIX = ".heic";

    // Match the code in MediaProvider.computeBucketValues().
    public static final String BUCKET_ID =
            String.valueOf(DIRECTORY.toLowerCase().hashCode());

    public static final long UNAVAILABLE = -1L;
    public static final long PREPARING = -2L;
    public static final long UNKNOWN_SIZE = -3L;
    public static final long LOW_STORAGE_THRESHOLD_BYTES = 60 * 1024 * 1024L;

    private static boolean sSaveSDCard = false;

    public static boolean isSaveSDCard() {
        return sSaveSDCard;
    }

    public static void setSaveSDCard(boolean saveSDCard) {
        sSaveSDCard = saveSDCard;
    }

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN)
    private static void setImageSize(ContentValues values, int width, int height) {
        // The two fields are available since ICS but got published in JB
        if (ApiHelper.HAS_MEDIA_COLUMNS_WIDTH_AND_HEIGHT) {
            values.put(MediaColumns.WIDTH, width);
            values.put(MediaColumns.HEIGHT, height);
        }
    }

    public static int writeFile(String path, byte[] jpeg, ExifInterface exif,
            String mimeType) {
        if (exif != null && (mimeType == null ||
            mimeType.equalsIgnoreCase("jpeg"))) {
            try {
                return exif.writeExif(jpeg, path);
            } catch (Exception e) {
                Log.e(TAG, "Failed to write data", e);
            }
        } else if (jpeg != null) {
            if (!(mimeType.equalsIgnoreCase("jpeg") || mimeType == null)) {
                 File dir = new File(RAW_DIRECTORY);
                 dir.mkdirs();
            }
            writeFile(path, jpeg);
            return jpeg.length;
        }
        return 0;
    }

    public static void writeFile(String path, byte[] data) {
        FileOutputStream out = null;
        try {
            out = new FileOutputStream(path);
            out.write(data);
        } catch (Exception e) {
            Log.e(TAG, "Failed to write data", e);
        } finally {
            try {
                out.close();
            } catch (Exception e) {
                Log.e(TAG, "Failed to close file after write", e);
            }
        }
    }

    // Save the image with a given mimeType and add it the MediaStore.
    public static Uri addImage(Context context, String title,
            ExifInterface exif, byte[] jpeg, String mimeType) {

        String path = generateFilepath(title, mimeType);
        writeFile(path, jpeg, exif, mimeType);
        return addImage(context, path);
    }

    // Get a ContentValues object for the given photo data
    public static ContentValues getContentValuesForData(String title,
            long date, Location location, int orientation, ExifInterface exif, int jpegLength,
            String path, int width, int height, String mimeType) {
        // Insert into MediaStore.
        ContentValues values = new ContentValues(9);
        values.put(ImageColumns.TITLE, title);
        if (mimeType.equalsIgnoreCase("jpeg") ||
            mimeType.equalsIgnoreCase("image/jpeg") ||
                mimeType.equalsIgnoreCase("heic") ||
            mimeType == null) {

            if (mimeType.equalsIgnoreCase("heic")){
                values.put(ImageColumns.DISPLAY_NAME, title + ".heic");
            } else if(mimeType.equalsIgnoreCase("heics")){
                values.put(ImageColumns.DISPLAY_NAME, title + ".heics");
            } else {
                values.put(ImageColumns.DISPLAY_NAME, title + ".jpg");
            }

        } else {
            values.put(ImageColumns.DISPLAY_NAME, title + ".raw");
        }
        values.put(ImageColumns.DATE_TAKEN, date);
        if (mimeType.equalsIgnoreCase("heic")) {
            values.put(ImageColumns.MIME_TYPE, "image/heif");
        } else {
            values.put(ImageColumns.MIME_TYPE, "image/jpeg");
        }
        // Clockwise rotation in degrees. 0, 90, 180, or 270.
        values.put(ImageColumns.ORIENTATION, orientation);
        values.put(ImageColumns.DATA, path);
        values.put(ImageColumns.SIZE, jpegLength);

        setImageSize(values, width, height);

        if (location != null) {
            values.put(ImageColumns.LATITUDE, location.getLatitude());
            values.put(ImageColumns.LONGITUDE, location.getLongitude());
        } else if (exif != null) {
            double[] latlng = exif.getLatLongAsDoubles();
            if (latlng != null) {
                values.put(Images.Media.LATITUDE, latlng[0]);
                values.put(Images.Media.LONGITUDE, latlng[1]);
            }
        }
        return values;
    }

    // Add the image to media store.
    public static Uri addImage(Context context, String path) {
        // Scan file
        MediaScannerConnection.scanFile(
                context,
                new String[] { path },
                null /* no mimetype, let scanner guess */,
                null /* no callback */);

         return Uri.fromFile(new File(path));
    }

    public static long addRawImage(String title, byte[] data,
                                  String mimeType) {
        String path = generateFilepath(title, mimeType);
        int size = writeFile(path, data, null, mimeType);
        // Try to get the real image size after add exif.
        File f = new File(path);
        if (f.exists() && f.isFile()) {
            size = (int) f.length();
        }
        return size;
    }

    // Overwrites the file and updates the MediaStore, or inserts the image if
    // one does not already exist.
    public static void updateImage(Uri imageUri, Context context, String title, long date,
            Location location, int orientation, ExifInterface exif, byte[] jpeg, int width,
            int height, String mimeType) {
        String path = generateFilepath(title, mimeType);
        writeFile(path, jpeg, exif, mimeType);
        updateImage(imageUri, context, title, date, location, orientation, jpeg.length, path,
                width, height, mimeType);
    }

    // Updates the image values in MediaStore, or inserts the image if one does
    // not already exist.
    public static void updateImage(Uri imageUri, Context context, String title,
            long date, Location location, int orientation, int jpegLength,
            String path, int width, int height, String mimeType) {

        ContentValues values =
                getContentValuesForData(title, date, location, orientation, null, jpegLength, path,
                        width, height, mimeType);

        // Update the MediaStore
        ContentResolver resolver = context.getContentResolver();
        int rowsModified = resolver.update(imageUri, values, null, null);

        if (rowsModified == 0) {
            // If no prior row existed, insert a new one.
            Log.w(TAG, "updateImage called with no prior image at uri: " + imageUri);
            insertImage(resolver, values);
        } else if (rowsModified != 1) {
            // This should never happen
            throw new IllegalStateException("Bad number of rows (" + rowsModified
                    + ") updated for uri: " + imageUri);
        }

        // Scan file
        MediaScannerConnection.scanFile(
                context,
                new String[] { path },
                null /* no mimetype, let scanner guess */,
                null /* no callback */);
    }

    public static void deleteImage(ContentResolver resolver, Uri uri) {
        try {
            resolver.delete(uri, null, null);
        } catch (Throwable th) {
            Log.e(TAG, "Failed to delete image: " + uri);
        }
    }

    public static String generateFilepath(String title, String pictureFormat) {
        if (pictureFormat == null || pictureFormat.equalsIgnoreCase("jpeg")
                || pictureFormat.equalsIgnoreCase("heic")
                || pictureFormat.equalsIgnoreCase("heics")) {
            String suffix = ".jpg";
            if (pictureFormat.equalsIgnoreCase("heic")) {
                suffix = ".heic";
            }else if(pictureFormat.equalsIgnoreCase("heics")) {
                suffix = ".heics";
            }
            if (isSaveSDCard() && SDCard.instance().isWriteable()) {
                return SDCard.instance().getDirectory() + '/' + title + suffix;
            } else {
                return DIRECTORY + '/' + title + suffix;
            }
        } else if (pictureFormat.equalsIgnoreCase("yuv")){
            String suffix = ".yuv";
            if (isSaveSDCard() && SDCard.instance().isWriteable()) {
                return SDCard.instance().getDirectory() + '/' + title + suffix;
            } else {
                return DIRECTORY + '/' + title + suffix;
            }
        } else {
            File dir = new File(RAW_DIRECTORY);
            dir.mkdirs();
            return RAW_DIRECTORY + '/' + title + ".raw";
        }
    }

    private static long getSDCardAvailableSpace() {
        if (SDCard.instance().isWriteable()) {
            File dir = new File(SDCard.instance().getDirectory());
            dir.mkdirs();
            try {
                StatFs stat = new StatFs(SDCard.instance().getDirectory());
                long ret = stat.getAvailableBlocks() * (long) stat.getBlockSize();
                return ret;
            } catch (Exception e) {
            }
            return UNKNOWN_SIZE;
        }
        return UNKNOWN_SIZE;
    }

    private static long getInternalStorageAvailableSpace() {
        String state = Environment.getExternalStorageState();
        Log.d(TAG, "External storage state=" + state);
        if (Environment.MEDIA_CHECKING.equals(state)) {
            return PREPARING;
        }
        if (!Environment.MEDIA_MOUNTED.equals(state)) {
            return UNAVAILABLE;
        }

        File dir = new File(DIRECTORY);
        dir.mkdirs();
        if (!dir.isDirectory() || !dir.canWrite()) {
            return UNAVAILABLE;
        }

        try {
            StatFs stat = new StatFs(DIRECTORY);
            return stat.getAvailableBlocks() * (long) stat.getBlockSize();
        } catch (Exception e) {
            Log.i(TAG, "Failed to access external storage", e);
        }
        return UNKNOWN_SIZE;
    }

    public static long getAvailableSpace() {
        if (isSaveSDCard()) {
            return getSDCardAvailableSpace();
        } else {
            return getInternalStorageAvailableSpace();
        }
    }

    public static long getTotalSpace(){
        String state = Environment.getExternalStorageState();
        Log.d(TAG, "External storage state=" + state);
        if (Environment.MEDIA_CHECKING.equals(state)) {
            return PREPARING;
        }
        if (!Environment.MEDIA_MOUNTED.equals(state)) {
            return UNAVAILABLE;
        }

        File dir = new File(DIRECTORY);
        dir.mkdirs();
        if (!dir.isDirectory() || !dir.canWrite()) {
            return UNAVAILABLE;
        }

        try {
            StatFs stat = new StatFs(Environment.getExternalStorageDirectory().getPath());
            return stat.getBlockCount() * (long) stat.getBlockSize();
        } catch (Exception e) {
            Log.i(TAG, "Failed to access external storage", e);
        }
        return UNKNOWN_SIZE;
    }

    public static boolean switchSavePath() {
        if (!isSaveSDCard()
                && getInternalStorageAvailableSpace() <= LOW_STORAGE_THRESHOLD_BYTES
                && getSDCardAvailableSpace() > LOW_STORAGE_THRESHOLD_BYTES) {
            setSaveSDCard(true);
            return true;
        }
        return false;
    }

    /**
     * OSX requires plugged-in USB storage to have path /DCIM/NNNAAAAA to be
     * imported. This is a temporary fix for bug#1655552.
     */
    public static void ensureOSXCompatible() {
        File nnnAAAAA = new File(DCIM, "100ANDRO");
        if (!(nnnAAAAA.exists() || nnnAAAAA.mkdirs())) {
            Log.e(TAG, "Failed to create " + nnnAAAAA.getPath());
        }
    }

    private static Uri insertImage(ContentResolver resolver, ContentValues values) {
        Uri uri = null;
        try {
            uri = resolver.insert(Images.Media.EXTERNAL_CONTENT_URI, values);
        } catch (Throwable th)  {
            // This can happen when the external volume is already mounted, but
            // MediaScanner has not notify MediaProvider to add that volume.
            // The picture is still safe and MediaScanner will find it and
            // insert it into MediaProvider. The only problem is that the user
            // cannot click the thumbnail to review the picture.
            Log.e(TAG, "Failed to write MediaStore" + th);
        }
        return uri;
    }
}
