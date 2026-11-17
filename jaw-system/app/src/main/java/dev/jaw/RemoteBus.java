package dev.jaw;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.media.AudioManager;
import android.os.Handler;
import android.os.Looper;
import android.view.KeyEvent;
import android.view.View;
import android.accessibilityservice.AccessibilityService;

/** Turns phone-remote commands into actions on the TV. Results are short messages for the phone. */
final class RemoteBus {
    private static final Handler UI = new Handler(Looper.getMainLooper());
    private static final String NEED_JAW = "Open Jaw System first / افتح جو سيستم أولاً";
    private static final String NEED_A11Y = "Turn on Back and Home in Jaw Settings > Remote / فعّل الرجوع والرئيسية من إعدادات جو";

    static String key(Context c, String k) {
        if (k == null) return "?";
        AudioManager am = (AudioManager) c.getSystemService(Context.AUDIO_SERVICE);
        switch (k) {
            case "up": return nav(KeyEvent.KEYCODE_DPAD_UP, View.FOCUS_UP);
            case "down": return nav(KeyEvent.KEYCODE_DPAD_DOWN, View.FOCUS_DOWN);
            case "left": return nav(KeyEvent.KEYCODE_DPAD_LEFT, View.FOCUS_LEFT);
            case "right": return nav(KeyEvent.KEYCODE_DPAD_RIGHT, View.FOCUS_RIGHT);
            case "ok": return nav(KeyEvent.KEYCODE_DPAD_CENTER, View.FOCUS_FORWARD);
            case "back": return back();
            case "home": return home(c);
            case "vol_up": am.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_RAISE, AudioManager.FLAG_SHOW_UI); return "ok";
            case "vol_down": am.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI); return "ok";
            case "mute": am.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_TOGGLE_MUTE, AudioManager.FLAG_SHOW_UI); return "ok";
            case "play": return media(am, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE);
            case "next": return media(am, KeyEvent.KEYCODE_MEDIA_NEXT);
            case "prev": return media(am, KeyEvent.KEYCODE_MEDIA_PREVIOUS);
            default: return "?";
        }
    }

    private static String media(AudioManager am, int code) {
        am.dispatchMediaKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, code));
        am.dispatchMediaKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, code));
        return "ok";
    }

    /** Arrow keys and OK only reach Jaw System's own screens. Other apps do not accept them from a normal app. */
    private static String nav(int code, int dir) {
        final Activity a = JawApp.top();
        if (a == null) return NEED_JAW;
        UI.post(() -> {
            boolean handled = a.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, code));
            a.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, code));
            if (!handled && code != KeyEvent.KEYCODE_DPAD_CENTER) {
                View f = a.getCurrentFocus();
                if (f != null) {
                    View n = f.focusSearch(dir);
                    if (n != null) n.requestFocus(dir);
                }
            }
        });
        return "ok";
    }

    private static String back() {
        final Activity a = JawApp.top();
        if (a != null) { UI.post(a::onBackPressed); return "ok"; }
        return JawAccessibilityService.act(AccessibilityService.GLOBAL_ACTION_BACK) ? "ok" : NEED_A11Y;
    }

    private static String home(Context c) {
        final Activity a = JawApp.top();
        Intent h = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        if (a != null) { UI.post(() -> a.startActivity(h)); return "ok"; }
        return JawAccessibilityService.act(AccessibilityService.GLOBAL_ACTION_HOME) ? "ok" : NEED_A11Y;
    }

    static String launch(Context c, String pkg) {
        if (pkg == null) return "?";
        final Intent li = Store.launch(c.getPackageManager(), pkg);
        if (li == null) return "Not installed / غير مثبت";
        final Activity a = JawApp.top();
        if (a != null) { UI.post(() -> a.startActivity(li)); return "ok"; }
        // Another app is in front: go Home first, then Android lets us open the app.
        if (JawAccessibilityService.act(AccessibilityService.GLOBAL_ACTION_HOME)) {
            UI.postDelayed(() -> { try { c.startActivity(li); } catch (Exception e) { /* blocked */ } }, 700);
            return "ok";
        }
        try { c.startActivity(li); return "ok"; } catch (Exception e) { return NEED_A11Y; }
    }
}
