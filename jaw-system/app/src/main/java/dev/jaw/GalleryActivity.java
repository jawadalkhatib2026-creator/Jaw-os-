package dev.jaw;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.KeyEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Toast;
import java.io.File;
import java.util.Arrays;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class GalleryActivity extends Activity {
    private static int counter = 0;
    private FrameLayout root;
    private LocalServer server;
    private final ExecutorService pool = Executors.newFixedThreadPool(2);
    private final Handler h = new Handler(Looper.getMainLooper());
    private File[] photos = new File[0];
    private FrameLayout viewer;
    private ImageView viewerImg;
    private int cur;
    private boolean slide;
    private final Runnable tick = new Runnable() {
        @Override public void run() {
            if (slide && viewer != null && photos.length > 0) {
                show((cur + 1) % photos.length);
                h.postDelayed(this, 4000);
            }
        }
    };

    static File dir(Context c) {
        File d = new File(c.getFilesDir(), "gallery");
        d.mkdirs();
        return d;
    }

    static Bitmap thumb(File f, int maxW) {
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(f.getPath(), o);
        int s = 1;
        while (o.outWidth / (s * 2) >= maxW) s *= 2;
        o.inJustDecodeBounds = false;
        o.inSampleSize = s;
        return BitmapFactory.decodeFile(f.getPath(), o);
    }

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        root = new FrameLayout(this);
        setContentView(root);
        render();
    }

    @Override protected void onDestroy() {
        super.onDestroy();
        h.removeCallbacks(tick);
        pool.shutdownNow();
        if (server != null) server.stop();
    }

    private void render() {
        File[] f = dir(this).listFiles();
        if (f == null) f = new File[0];
        Arrays.sort(f, (a, c) -> Long.compare(c.lastModified(), a.lastModified()));
        photos = f;
        root.removeAllViews();
        viewer = null;
        LinearLayout[] pg = new LinearLayout[1];
        ScrollView sv = Ui.screen(this, getString(R.string.jaw_gallery), pg);
        View first = Ui.row(this, pg[0], getString(R.string.gal_add_phone), null, v -> addFromPhone());
        if (photos.length == 0) {
            pg[0].addView(Ui.text(this, getString(R.string.gal_empty), 18, 0xCCFFFFFF));
        } else {
            GridLayout grid = new GridLayout(this);
            grid.setColumnCount(4);
            for (int i = 0; i < photos.length; i++) {
                final int idx = i;
                final File file = photos[i];
                FrameLayout cell = new FrameLayout(this);
                cell.setFocusable(true);
                cell.setClickable(true);
                cell.setBackground(Ui.pill(this, 0x22FFFFFF));
                ImageView iv = new ImageView(this);
                iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
                cell.addView(iv, new FrameLayout.LayoutParams(-1, -1));
                pool.execute(() -> {
                    Bitmap bm = thumb(file, 400);
                    if (bm != null) runOnUiThread(() -> iv.setImageBitmap(bm));
                });
                cell.setOnFocusChangeListener((v, focused) -> {
                    float s = focused ? 1.1f : 1f;
                    v.animate().scaleX(s).scaleY(s).setDuration(120).start();
                    v.setElevation(focused ? 12f : 0f);
                });
                cell.setOnClickListener(v -> openViewer(idx));
                cell.setOnLongClickListener(v -> { confirmDelete(file); return true; });
                GridLayout.LayoutParams lp = new GridLayout.LayoutParams();
                lp.width = Ui.dp(this, 160);
                lp.height = Ui.dp(this, 100);
                lp.setMargins(Ui.dp(this, 6), Ui.dp(this, 6), Ui.dp(this, 6), Ui.dp(this, 6));
                grid.addView(cell, lp);
            }
            pg[0].addView(grid);
            pg[0].addView(Ui.text(this, getString(R.string.gal_hint), 14, 0x80FFFFFF));
        }
        root.addView(sv);
        first.requestFocus();
    }

    private void confirmDelete(File f) {
        new AlertDialog.Builder(this).setMessage(getString(R.string.files_delete_q, ""))
            .setPositiveButton(R.string.ok, (d, w) -> {
                f.delete();
                Toast.makeText(this, R.string.deleted, Toast.LENGTH_SHORT).show();
                render();
            })
            .setNegativeButton(R.string.cancel, null).show();
    }

    private void addFromPhone() {
        if (server != null) server.stop();
        server = new LocalServer(this, (route, type, f) -> {
            File dest = new File(dir(this), System.currentTimeMillis() + "_" + (counter++) + ".jpg");
            if (!f.renameTo(dest)) return "Failed / \u0641\u0634\u0644";
            runOnUiThread(() -> { if (viewer == null) render(); });
            return null;
        });
        String url = server.start() ? server.url("gallery") : null;
        if (url == null) {
            Toast.makeText(this, R.string.conn_none, Toast.LENGTH_LONG).show();
            server.stop();
            return;
        }
        Qr.show(this, getString(R.string.gal_add_phone), url, server);
    }

    private void openViewer(int idx) {
        viewer = new FrameLayout(this);
        viewer.setBackgroundColor(Color.BLACK);
        viewerImg = new ImageView(this);
        viewerImg.setScaleType(ImageView.ScaleType.FIT_CENTER);
        viewer.addView(viewerImg, new FrameLayout.LayoutParams(-1, -1));
        root.addView(viewer, new FrameLayout.LayoutParams(-1, -1));
        show(idx);
    }

    private void show(int i) {
        cur = i;
        viewerImg.setImageBitmap(thumb(photos[i], 1920));
    }

    private void closeViewer() {
        slide = false;
        h.removeCallbacks(tick);
        root.removeView(viewer);
        viewer = null;
    }

    @Override public boolean onKeyDown(int code, KeyEvent e) {
        if (viewer != null && photos.length > 0) {
            if (code == KeyEvent.KEYCODE_DPAD_RIGHT) { show((cur + 1) % photos.length); return true; }
            if (code == KeyEvent.KEYCODE_DPAD_LEFT) { show((cur - 1 + photos.length) % photos.length); return true; }
            if (code == KeyEvent.KEYCODE_DPAD_CENTER || code == KeyEvent.KEYCODE_ENTER) {
                slide = !slide;
                h.removeCallbacks(tick);
                if (slide) h.postDelayed(tick, 4000);
                return true;
            }
        }
        return super.onKeyDown(code, e);
    }

    @Override public void onBackPressed() {
        if (viewer != null) closeViewer(); else super.onBackPressed();
    }
}
