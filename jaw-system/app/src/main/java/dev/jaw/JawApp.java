package dev.jaw;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.os.Bundle;

/** Starts the phone-remote server and remembers which Jaw screen is in front, so remote keys reach it. */
public class JawApp extends Application {
    private static JawApp inst;
    private volatile Activity top;

    @Override public void onCreate() {
        super.onCreate();
        inst = this;
        registerActivityLifecycleCallbacks(new ActivityLifecycleCallbacks() {
            @Override public void onActivityResumed(Activity a) { top = a; }
            @Override public void onActivityPaused(Activity a) { if (top == a) top = null; }
            @Override public void onActivityCreated(Activity a, Bundle b) { }
            @Override public void onActivityStarted(Activity a) { }
            @Override public void onActivityStopped(Activity a) { }
            @Override public void onActivitySaveInstanceState(Activity a, Bundle b) { }
            @Override public void onActivityDestroyed(Activity a) { }
        });
        applyRemote(this);
    }

    static Activity top() { return inst == null ? null : inst.top; }

    static void applyRemote(Context c) {
        if (Store.remoteOn(c)) RemoteServer.start(c.getApplicationContext());
        else RemoteServer.stop();
    }
}
