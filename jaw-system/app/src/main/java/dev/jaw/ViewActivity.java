package dev.jaw;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.view.KeyEvent;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.VideoView;
import java.io.File;

/** Full-screen photo or video from Jaw Files. */
public class ViewActivity extends Activity {
    private VideoView video;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);
        setContentView(root);
        String path = getIntent().getStringExtra("path");
        if (path == null) { finish(); return; }
        File f = new File(path);
        if (Media.VIDEO.contains(Media.ext(f.getName()))) {
            video = new VideoView(this);
            root.addView(video, new FrameLayout.LayoutParams(-1, -1));
            video.setVideoPath(path);
            video.start();
        } else {
            ImageView iv = new ImageView(this);
            iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
            iv.setImageBitmap(GalleryActivity.thumb(f, 1920));
            root.addView(iv, new FrameLayout.LayoutParams(-1, -1));
        }
    }

    @Override public boolean onKeyDown(int code, KeyEvent e) {
        if (video != null && (code == KeyEvent.KEYCODE_DPAD_CENTER || code == KeyEvent.KEYCODE_ENTER
            || code == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)) {
            if (video.isPlaying()) video.pause(); else video.start();
            return true;
        }
        return super.onKeyDown(code, e);
    }

    @Override protected void onDestroy() {
        super.onDestroy();
        if (video != null) video.stopPlayback();
    }
}
