package dev.jaw;

import android.accessibilityservice.AccessibilityService;
import android.view.accessibility.AccessibilityEvent;

/** Does nothing except let the phone remote press Back and Home while another app is open. */
public class JawAccessibilityService extends AccessibilityService {
    static volatile JawAccessibilityService inst;

    @Override protected void onServiceConnected() { inst = this; }

    @Override public boolean onUnbind(android.content.Intent i) {
        inst = null;
        return super.onUnbind(i);
    }

    @Override public void onAccessibilityEvent(AccessibilityEvent e) { }

    @Override public void onInterrupt() { }

    static boolean act(int action) {
        JawAccessibilityService s = inst;
        return s != null && s.performGlobalAction(action);
    }
}
