package dev.jaw;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.graphics.Bitmap;
import android.graphics.PixelFormat;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.Image;
import android.media.ImageReader;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.util.DisplayMetrics;
import java.io.ByteArrayOutputStream;

/** Copies the TV screen into small JPEG frames that the phone remote shows. Protected video (Netflix etc.) appears black. */
public class ScreenService extends Service {
    static volatile byte[] latest;
    static volatile boolean running;
    private MediaProjection mp;
    private VirtualDisplay vd;
    private ImageReader ir;
    private HandlerThread ht;
    private long last;

    @Override public IBinder onBind(Intent i) { return null; }

    @SuppressWarnings("deprecation")
    @Override public int onStartCommand(Intent i, int flags, int id) {
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        nm.createNotificationChannel(new NotificationChannel("jaw_screen", "Screen view", NotificationManager.IMPORTANCE_LOW));
        Notification n = new Notification.Builder(this, "jaw_screen")
            .setContentTitle(getString(R.string.screen_notif))
            .setSmallIcon(android.R.drawable.ic_menu_view).build();
        if (Build.VERSION.SDK_INT >= 29) startForeground(7, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION);
        else startForeground(7, n);
        if (i == null) { stopSelf(); return START_NOT_STICKY; }
        try {
            Intent data = i.getParcelableExtra("data");
            MediaProjectionManager m = (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);
            mp = m.getMediaProjection(i.getIntExtra("code", 0), data);
            ht = new HandlerThread("jaw-screen");
            ht.start();
            Handler h = new Handler(ht.getLooper());
            mp.registerCallback(new MediaProjection.Callback() {
                @Override public void onStop() { stopSelf(); }
            }, h);
            DisplayMetrics dm = getResources().getDisplayMetrics();
            final int w = Math.max(2, dm.widthPixels / 2), hh = Math.max(2, dm.heightPixels / 2);
            ir = ImageReader.newInstance(w, hh, PixelFormat.RGBA_8888, 2);
            vd = mp.createVirtualDisplay("jaw", w, hh, dm.densityDpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, ir.getSurface(), null, h);
            ir.setOnImageAvailableListener(r -> grab(r, w, hh), h);
            running = true;
        } catch (Exception e) {
            stopSelf();
        }
        return START_NOT_STICKY;
    }

    private void grab(ImageReader r, int w, int h) {
        Image img = null;
        try {
            img = r.acquireLatestImage();
            if (img == null) return;
            long now = System.currentTimeMillis();
            if (now - last < 350) return;
            last = now;
            Image.Plane p = img.getPlanes()[0];
            Bitmap bmp = Bitmap.createBitmap(p.getRowStride() / p.getPixelStride(), h, Bitmap.Config.ARGB_8888);
            bmp.copyPixelsFromBuffer(p.getBuffer());
            Bitmap crop = Bitmap.createBitmap(bmp, 0, 0, w, h);
            ByteArrayOutputStream o = new ByteArrayOutputStream();
            crop.compress(Bitmap.CompressFormat.JPEG, 55, o);
            latest = o.toByteArray();
        } catch (Exception e) {
            /* skip this frame */
        } finally {
            if (img != null) img.close();
        }
    }

    @Override public void onDestroy() {
        super.onDestroy();
        running = false;
        latest = null;
        try { if (vd != null) vd.release(); } catch (Exception e) { }
        try { if (ir != null) ir.close(); } catch (Exception e) { }
        try { if (mp != null) mp.stop(); } catch (Exception e) { }
        if (ht != null) ht.quitSafely();
        stopForeground(true);
    }
}
