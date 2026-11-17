package dev.jaw;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.provider.Settings;
import android.widget.Toast;
import androidx.core.content.FileProvider;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import org.json.JSONArray;
import org.json.JSONObject;

/** Checks the newest GitHub release of the project and installs its APK. */
final class Updater {
    static final String REPO = "jawadalkhatib2026-creator/JAW-OS";

    @SuppressWarnings("deprecation")
    static int version(Activity a) {
        try { return a.getPackageManager().getPackageInfo(a.getPackageName(), 0).versionCode; }
        catch (Exception e) { return 0; }
    }

    private static void toast(Activity a, int res) {
        a.runOnUiThread(() -> Toast.makeText(a, res, Toast.LENGTH_LONG).show());
    }

    static void check(Activity a) {
        Toast.makeText(a, R.string.update_checking, Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            try {
                HttpURLConnection c = (HttpURLConnection)
                    new URL("https://api.github.com/repos/" + REPO + "/releases/latest").openConnection();
                c.setRequestProperty("Accept", "application/vnd.github+json");
                c.setConnectTimeout(8000);
                c.setReadTimeout(8000);
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                try (InputStream in = c.getInputStream()) {
                    byte[] buf = new byte[8192];
                    int r;
                    while ((r = in.read(buf)) != -1) out.write(buf, 0, r);
                }
                JSONObject o = new JSONObject(out.toString("UTF-8"));
                int latest = Integer.parseInt(o.getString("tag_name").replaceAll("[^0-9]", ""));
                String apk = null;
                JSONArray assets = o.getJSONArray("assets");
                for (int i = 0; i < assets.length(); i++) {
                    JSONObject as = assets.getJSONObject(i);
                    if (as.getString("name").endsWith(".apk")) { apk = as.getString("browser_download_url"); break; }
                }
                final int cur = version(a);
                final String url = apk;
                if (url == null || latest <= cur) {
                    a.runOnUiThread(() -> Toast.makeText(a, a.getString(R.string.update_latest, cur), Toast.LENGTH_LONG).show());
                } else {
                    final int v = latest;
                    a.runOnUiThread(() -> new AlertDialog.Builder(a)
                        .setMessage(a.getString(R.string.update_available, v))
                        .setPositiveButton(R.string.update_install, (d, w) -> download(a, url))
                        .setNegativeButton(R.string.cancel, null).show());
                }
            } catch (Exception e) {
                toast(a, R.string.update_failed);
            }
        }).start();
    }

    private static void download(Activity a, String url) {
        Toast.makeText(a, R.string.update_downloading, Toast.LENGTH_LONG).show();
        new Thread(() -> {
            try {
                File f = new File(a.getCacheDir(), "update.apk");
                HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
                c.setConnectTimeout(10000);
                c.setReadTimeout(20000);
                try (InputStream in = c.getInputStream(); FileOutputStream o = new FileOutputStream(f)) {
                    byte[] buf = new byte[65536];
                    int r;
                    while ((r = in.read(buf)) != -1) o.write(buf, 0, r);
                }
                a.runOnUiThread(() -> install(a, f));
            } catch (Exception e) {
                toast(a, R.string.update_failed);
            }
        }).start();
    }

    private static void install(Activity a, File f) {
        if (!a.getPackageManager().canRequestPackageInstalls()) {
            Toast.makeText(a, R.string.update_allow, Toast.LENGTH_LONG).show();
            try {
                a.startActivity(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:" + a.getPackageName())));
            } catch (Exception e) { /* user can open it from Settings > Apps */ }
            return;
        }
        try {
            Uri u = FileProvider.getUriForFile(a, a.getPackageName() + ".fileprovider", f);
            a.startActivity(new Intent(Intent.ACTION_VIEW)
                .setDataAndType(u, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (Exception e) {
            toast(a, R.string.update_failed);
        }
    }
}
