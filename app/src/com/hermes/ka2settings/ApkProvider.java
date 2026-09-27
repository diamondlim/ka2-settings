package com.hermes.ka2settings;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;

import java.io.File;
import java.io.FileNotFoundException;

/**
 * Serves the downloaded update APK to the system package installer.
 *
 * Android 7 and later refuse file:// URIs across apps (FileUriExposedException), so the APK has to
 * go out as a content:// URI. This is the whole of that: the app has no library dependencies and no
 * AndroidX, so instead of pulling in FileProvider this provider exposes exactly one thing - files in
 * this app's own cache directory, read-only - and grants a one-shot read permission with the intent.
 */
public class ApkProvider extends ContentProvider {

    public static Uri uriFor(Context c, File file) {
        return new Uri.Builder()
                .scheme("content")
                .authority(c.getPackageName() + ".apk")
                .appendPath(file.getName())
                .build();
    }

    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public String getType(Uri uri) {
        return "application/vnd.android.package-archive";
    }

    @Override
    public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        Context c = getContext();
        if (c == null) {
            throw new FileNotFoundException("no context");
        }
        String name = uri.getLastPathSegment();
        if (name == null || name.contains("/") || name.contains("..")) {
            throw new FileNotFoundException("bad name");
        }
        File file = new File(c.getCacheDir(), name);
        if (!file.isFile()) {
            throw new FileNotFoundException(name);
        }
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY);
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] args, String sort) {
        return null;
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        return null;
    }

    @Override
    public int delete(Uri uri, String selection, String[] args) {
        return 0;
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] args) {
        return 0;
    }
}
