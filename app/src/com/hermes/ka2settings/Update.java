package com.hermes.ka2settings;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.List;
import java.util.Map;

/**
 * Self-update from this project's GitHub releases.
 *
 * The app checks https://api.github.com/repos/diamondlim/ka2-settings/releases/latest on startup and
 * when the Update button is tapped. A newer release turns the button into "Install vX.Y": tapping it
 * downloads the APK into the app cache, hands it to the system package installer through
 * {@link ApkProvider}, and Android asks for the usual confirmation tap.
 *
 * Two things this deliberately does NOT do: it never installs silently (that needs a device-owner
 * app), and it never trusts a tag alone - the downloaded APK's own versionCode has to be greater than
 * the installed one before the installer is opened, so a mistagged release cannot downgrade the car.
 *
 * Release assets are named KA2Settings-v7.7-57.apk, i.e. the versionCode is in the file name, so the
 * cheap tag comparison can be confirmed exactly before downloading anything.
 *
 * On a unit with no APK installer at all - the car's head unit - the check still runs and still reports
 * the newer release, but the update itself happens over ADB from a host the unit trusts
 * (install_over_adb.sh in this repo). {@link #hasApkInstaller} is what decides which of the two the
 * button offers, so the failure is named before the download rather than after it.
 */
public final class Update {

    private static final String TAG = "KA2Settings";
    private static final String API =
            "https://api.github.com/repos/diamondlim/ka2-settings/releases/latest";
    /** Where a person (or a host with adb) fetches the build from: the release page, not the API. */
    private static final String RELEASES =
            "https://github.com/diamondlim/ka2-settings/releases/latest";
    private static final Handler UI = new Handler(Looper.getMainLooper());

    /** Set once a newer release is known: what to fetch, and its label. */
    private static volatile String readyUrl;
    private static volatile String readyTag;
    private static volatile int readyCode;
    private static volatile boolean checking;
    private static volatile boolean installing;

    private Update() {
    }

    // ------------------------------------------------------------------ what is installed

    static String installedName(Context c) {
        try {
            PackageInfo p = c.getPackageManager().getPackageInfo(c.getPackageName(), 0);
            return p.versionName == null ? "?" : p.versionName;
        } catch (Exception e) {
            return "?";
        }
    }

    @SuppressWarnings("deprecation")
    static int installedCode(Context c) {
        try {
            PackageInfo p = c.getPackageManager().getPackageInfo(c.getPackageName(), 0);
            return Build.VERSION.SDK_INT >= 28
                    ? (int) p.getLongVersionCode()
                    : p.versionCode;
        } catch (Exception e) {
            return 0;
        }
    }

    // ------------------------------------------------------------------ wiring and checking

    /** Wire a status line and a button, and check quietly in the background. */
    public static void attach(final Context ctx, final TextView status, final Button button) {
        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (installing) {
                    return;
                }
                if (readyUrl != null) {
                    if (!hasApkInstaller(ctx)) {
                        say(status, "no APK installer on this unit - update over ADB: run "
                                + "install_over_adb.sh on a host the unit trusts ("
                                + readyTag + " from " + RELEASES + ")");
                        return;
                    }
                    install(ctx, status, button);
                } else {
                    check(ctx, status, button, true);
                }
            }
        });
        check(ctx, status, button, false);
    }

    private static void check(final Context ctx, final TextView status, final Button button,
                              final boolean manual) {
        if (checking) {
            return;
        }
        checking = true;
        final int haveCode = installedCode(ctx);
        final String haveName = installedName(ctx);
        if (manual) {
            say(status, "checking github...");
        }
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    final Map<String, Object> rel = Json.parseObject(get(API));
                    final String tag = Json.str(rel, "tag_name", "");
                    String url = null;
                    int code = 0;
                    List<Object> assets = Json.list(rel, "assets");
                    if (assets != null) {
                        for (Object asset : assets) {
                            if (!(asset instanceof Map)) {
                                continue;
                            }
                            @SuppressWarnings("unchecked")
                            Map<String, Object> m = (Map<String, Object>) asset;
                            String name = Json.str(m, "name", "");
                            if (!name.endsWith(".apk")) {
                                continue;
                            }
                            url = Json.str(m, "browser_download_url", null);
                            code = codeFromName(name);
                            break;
                        }
                    }
                    final String furi = url;
                    final int fcode = code;
                    UI.post(new Runnable() {
                        @Override
                        public void run() {
                            checking = false;
                            if (furi == null) {
                                say(status, "release " + tag + " has no APK attached");
                                return;
                            }
                            readyUrl = null;
                            readyTag = tag;
                            readyCode = fcode;
                            boolean newer = fcode > 0 ? fcode > haveCode : compareTag(tag, haveName) > 0;
                            if (!newer) {
                                say(status, "up to date - " + haveName + " is the newest release");
                                return;
                            }
                            readyUrl = furi;
                            if (hasApkInstaller(ctx)) {
                                button.setText("Install " + tag);
                                say(status, tag + " is available (installed " + haveName + ")");
                            } else {
                                button.setText("Update over ADB");
                                say(status, tag + " is available (installed " + haveName
                                        + ") - this unit has no APK installer");
                            }
                        }
                    });
                } catch (final Exception e) {
                    UI.post(new Runnable() {
                        @Override
                        public void run() {
                            checking = false;
                            say(status, "update check failed: " + brief(e));
                        }
                    });
                }
            }
        }, "ka2-update-check").start();
    }

    // ------------------------------------------------------------------ installing

    private static void install(final Context ctx, final TextView status, final Button button) {
        final String url = readyUrl;
        final String tag = readyTag;
        final int code = readyCode;
        if (url == null) {
            return;
        }
        if (Build.VERSION.SDK_INT >= 26 && !ctx.getPackageManager().canRequestPackageInstalls()) {
            say(status, "allow KA2 Settings to install apps, then tap again");
            try {
                Intent allow = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                        Uri.parse("package:" + ctx.getPackageName()));
                allow.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                ctx.startActivity(allow);
            } catch (Exception e) {
                Log.w(TAG, "cannot open the unknown-sources screen: " + e);
            }
            return;
        }
        installing = true;
        button.setText("Installing...");
        say(status, "downloading " + tag + "...");
        new Thread(new Runnable() {
            @Override
            public void run() {
                File apk = new File(ctx.getCacheDir(), "KA2Settings-" + tag + ".apk");
                try {
                    download(url, apk);
                    PackageInfo pi = ctx.getPackageManager()
                            .getPackageArchiveInfo(apk.getAbsolutePath(), 0);
                    if (pi == null) {
                        throw new IllegalStateException("downloaded file is not a readable APK");
                    }
                    if (code > 0 && pi.versionCode < code) {
                        throw new IllegalStateException("downloaded APK is older than its release");
                    }
                    final Uri uri = ApkProvider.uriFor(ctx, apk);
                    UI.post(new Runnable() {
                        @Override
                        public void run() {
                            installing = false;
                            say(status, "installer opened - confirm on this screen");
                            Intent view = new Intent(Intent.ACTION_VIEW);
                            view.setDataAndType(uri, "application/vnd.android.package-archive");
                            view.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
                                    | Intent.FLAG_ACTIVITY_NEW_TASK);
                            try {
                                ctx.startActivity(view);
                            } catch (Exception e) {
                                say(status, "no installer on this unit accepted the APK ("
                                        + brief(e) + ") - update over ADB instead");
                            }
                        }
                    });
                } catch (final Exception e) {
                    UI.post(new Runnable() {
                        @Override
                        public void run() {
                            installing = false;
                            button.setText("Retry install");
                            say(status, "download failed: " + brief(e));
                        }
                    });
                }
            }
        }, "ka2-update-download").start();
    }

    // ------------------------------------------------------------------ helpers

    /**
     * Whether this unit will take an APK itself.
     *
     * The car's head unit ships no activity for application/vnd.android.package-archive, and no intent
     * construction can conjure one: without this check the app downloads the release and then reports a
     * failure at the last step. Asked up front, the same fact becomes an instruction. On an unknown
     * answer it returns true, so a unit that cannot be probed keeps the old behaviour rather than being
     * locked out of updating.
     */
    static boolean hasApkInstaller(Context c) {
        try {
            Intent probe = new Intent(Intent.ACTION_VIEW);
            probe.setDataAndType(Uri.fromFile(new File(c.getCacheDir(), "probe.apk")),
                    "application/vnd.android.package-archive");
            return !c.getPackageManager().queryIntentActivities(probe, 0).isEmpty();
        } catch (Exception e) {
            return true;
        }
    }

    private static void say(final TextView status, final String text) {
        if (status == null) {
            return;
        }
        UI.post(new Runnable() {
            @Override
            public void run() {
                status.setText(text);
            }
        });
    }

    private static String get(String url) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        try {
            c.setConnectTimeout(10000);
            c.setReadTimeout(15000);
            c.setRequestProperty("User-Agent", "KA2Settings");
            c.setRequestProperty("Accept", "application/vnd.github+json");
            int code = c.getResponseCode();
            if (code != 200) {
                throw new IllegalStateException("github answered " + code);
            }
            return read(c.getInputStream());
        } finally {
            c.disconnect();
        }
    }

    private static void download(String url, File into) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        FileOutputStream out = null;
        try {
            c.setConnectTimeout(10000);
            c.setReadTimeout(30000);
            c.setRequestProperty("User-Agent", "KA2Settings");
            c.setInstanceFollowRedirects(true);
            int code = c.getResponseCode();
            if (code != 200) {
                throw new IllegalStateException("download answered " + code);
            }
            InputStream in = c.getInputStream();
            out = new FileOutputStream(into);
            byte[] buf = new byte[16384];
            int n;
            while ((n = in.read(buf)) > 0) {
                out.write(buf, 0, n);
            }
            out.flush();
        } finally {
            if (out != null) {
                out.close();
            }
            c.disconnect();
        }
    }

    private static String read(InputStream in) throws Exception {
        StringBuilder sb = new StringBuilder();
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) > 0) {
            sb.append(new String(buf, 0, n, "UTF-8"));
        }
        in.close();
        return sb.toString();
    }

    /** versionCode out of an asset named "...-57.apk", 0 when the name does not carry one. */
    private static int codeFromName(String asset) {
        int dot = asset.lastIndexOf('.');
        int dash = asset.lastIndexOf('-', dot < 0 ? asset.length() - 1 : dot);
        if (dash < 0) {
            return 0;
        }
        String tail = asset.substring(dash + 1, dot < 0 ? asset.length() : dot);
        try {
            return Integer.parseInt(tail.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /** Compare "v7.7" against an installed "7.6": positive when the tag is newer. */
    private static int compareTag(String tag, String installed) {
        int[] a = parts(tag);
        int[] b = parts(installed);
        for (int i = 0; i < Math.max(a.length, b.length); i++) {
            int x = i < a.length ? a[i] : 0;
            int y = i < b.length ? b[i] : 0;
            if (x != y) {
                return x - y;
            }
        }
        return 0;
    }

    private static int[] parts(String version) {
        String s = version == null ? "" : version.trim();
        while (s.startsWith("v") || s.startsWith("V")) {
            s = s.substring(1);
        }
        String[] bits = s.split("[^0-9]+");
        int[] out = new int[bits.length];
        for (int i = 0; i < bits.length; i++) {
            try {
                out[i] = Integer.parseInt(bits[i]);
            } catch (NumberFormatException e) {
                out[i] = 0;
            }
        }
        return out;
    }

    private static String brief(Exception e) {
        String m = e.getMessage();
        return m == null ? e.getClass().getSimpleName() : m;
    }
}
