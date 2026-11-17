package dev.jaw;

import android.content.Context;
import android.content.Intent;
import android.provider.Settings;
import android.widget.Toast;

final class Sys {
    private static boolean open(Context c, String action) {
        try {
            c.startActivity(new Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    static void tvSettings(Context c) {
        if (!open(c, Settings.ACTION_SETTINGS)) Toast.makeText(c, R.string.cant_open, Toast.LENGTH_SHORT).show();
    }

    static void dateSettings(Context c) {
        if (!open(c, Settings.ACTION_DATE_SETTINGS)) tvSettings(c);
    }

    /** Apps can't make themselves the default home app, so this opens the system's own picker. */
    static void homeSettings(Context c) {
        if (open(c, Settings.ACTION_HOME_SETTINGS)) return;
        Toast.makeText(c, R.string.home_fallback, Toast.LENGTH_LONG).show();
        tvSettings(c);
    }
}
