package dev.jaw;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.Settings;
import android.widget.Toast;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

final class Media {
    static final Set<String> AUDIO = new HashSet<>(Arrays.asList("mp3", "m4a", "aac", "flac", "wav", "ogg", "opus", "mka"));
    static final Set<String> IMAGE = new HashSet<>(Arrays.asList("jpg", "jpeg", "png", "webp", "gif", "bmp"));
    static final Set<String> VIDEO = new HashSet<>(Arrays.asList("mp4", "mkv", "avi", "mov", "webm", "ts", "m4v", "3gp"));

    static String ext(String name) {
        int i = name.lastIndexOf('.');
        return i < 0 ? "" : name.substring(i + 1).toLowerCase(Locale.US);
    }

    /** True when the app can read USB/SD files. Otherwise opens the permission screen and returns false. */
    static boolean ensureAccess(Activity a) {
        if (Build.VERSION.SDK_INT >= 30) {
            if (Environment.isExternalStorageManager()) return true;
            Toast.makeText(a, R.string.files_perm, Toast.LENGTH_LONG).show();
            try {
                a.startActivity(new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                    Uri.parse("package:" + a.getPackageName())));
            } catch (Exception e) {
                try { a.startActivity(new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)); }
                catch (Exception e2) { /* user can grant it from Settings > Apps */ }
            }
            return false;
        }
        if (a.checkSelfPermission(android.Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            a.requestPermissions(new String[]{android.Manifest.permission.READ_EXTERNAL_STORAGE,
                android.Manifest.permission.WRITE_EXTERNAL_STORAGE}, 1);
            return false;
        }
        return true;
    }
}
