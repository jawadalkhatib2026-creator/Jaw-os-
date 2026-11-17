package dev.jaw;

import android.app.Activity;
import android.graphics.Color;
import android.media.MediaMetadataRetriever;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import android.graphics.drawable.ColorDrawable;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/** Plays music from USB drives, SD cards and the Music folder. Playback stops when you leave the screen. */
public class MusicActivity extends Activity {
    private static final String PLAY = "\u25B6", PAUSE = "\u275A\u275A";
    private final List<File> tracks = new ArrayList<>();
    private final Handler h = new Handler(Looper.getMainLooper());
    private final Random rnd = new Random();
    private ArrayAdapter<File> adapter;
    private ListView lv;
    private TextView nowTitle, nowSub, playBtn, shuffleBtn;
    private ProgressBar progress;
    private MediaPlayer mp;
    private int index = -1;
    private boolean shuffle;
    private final Runnable ticker = new Runnable() {
        @Override public void run() {
            try {
                if (mp != null && mp.isPlaying()) progress.setProgress(mp.getCurrentPosition() * 1000 / Math.max(1, mp.getDuration()));
            } catch (IllegalStateException e) { /* player is changing track */ }
            h.postDelayed(this, 500);
        }
    };

    private TextView button(String glyph, View.OnClickListener click) {
        TextView t = Ui.text(this, glyph, 22, Color.WHITE);
        t.setGravity(Gravity.CENTER);
        t.setFocusable(true);
        t.setClickable(true);
        t.setBackground(Ui.pill(this, 0x22FFFFFF));
        t.setOnFocusChangeListener((v, f) -> v.setBackground(Ui.pill(this, f ? 0x66FFFFFF : 0x22FFFFFF)));
        t.setOnClickListener(click);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Ui.dp(this, 90), Ui.dp(this, 56));
        lp.setMarginEnd(Ui.dp(this, 14));
        ((LinearLayout) controls).addView(t, lp);
        return t;
    }

    private LinearLayout controls;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setBackground(Theme.background(Store.theme(this)));
        page.setPadding(Ui.dp(this, 100), Ui.dp(this, 36), Ui.dp(this, 100), Ui.dp(this, 20));
        page.addView(Ui.text(this, getString(R.string.jaw_music), 30, Color.WHITE));

        nowTitle = Ui.text(this, "\u2014", 24, Color.WHITE);
        nowTitle.setSingleLine();
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(-2, -2);
        tp.topMargin = Ui.dp(this, 18);
        page.addView(nowTitle, tp);
        nowSub = Ui.text(this, getString(R.string.music_scanning), 16, 0xB3FFFFFF);
        nowSub.setSingleLine();
        page.addView(nowSub);

        progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progress.setMax(1000);
        LinearLayout.LayoutParams pp = new LinearLayout.LayoutParams(-1, Ui.dp(this, 8));
        pp.topMargin = Ui.dp(this, 14);
        page.addView(progress, pp);

        controls = new LinearLayout(this);
        controls.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(-2, -2);
        cp.topMargin = Ui.dp(this, 14);
        cp.bottomMargin = Ui.dp(this, 14);
        page.addView(controls, cp);
        button("|\u25C0", v -> prev());
        playBtn = button(PLAY, v -> toggle());
        button("\u25B6|", v -> next());
        shuffleBtn = button("\u21C4", v -> {
            shuffle = !shuffle;
            shuffleBtn.setTextColor(shuffle ? 0xFF74C0FC : Color.WHITE);
        });

        adapter = new ArrayAdapter<File>(this, android.R.layout.simple_list_item_1, tracks) {
            @Override public View getView(int p, View cv, ViewGroup parent) {
                TextView t = (TextView) super.getView(p, cv, parent);
                String n = getItem(p).getName();
                int dot = n.lastIndexOf('.');
                t.setText((p == index ? "\u25B6  " : "     ") + (dot > 0 ? n.substring(0, dot) : n));
                t.setTextColor(p == index ? 0xFF74C0FC : Color.WHITE);
                t.setTextSize(18);
                t.setSingleLine();
                return t;
            }
        };
        lv = new ListView(this);
        lv.setAdapter(adapter);
        lv.setDivider(null);
        lv.setSelector(new ColorDrawable(0x40FFFFFF));
        lv.setOnItemClickListener((p, v, pos, id) -> play(pos));
        page.addView(lv, new LinearLayout.LayoutParams(-1, 0, 1f));
        setContentView(page);

        h.post(ticker);
        if (Media.ensureAccess(this)) scan(); else nowSub.setText(R.string.files_perm);
    }

    @Override protected void onResume() {
        super.onResume();
        if (tracks.isEmpty() && Media.ensureAccess(this)) scan();
    }

    @Override public void onRequestPermissionsResult(int code, String[] perms, int[] res) {
        super.onRequestPermissionsResult(code, perms, res);
        if (Media.ensureAccess(this)) scan();
    }

    private void scan() {
        nowSub.setText(R.string.music_scanning);
        new Thread(() -> {
            List<File> out = new ArrayList<>();
            File internal = Environment.getExternalStorageDirectory();
            for (String d : new String[]{"Music", "Download", "Podcasts", "Documents", "Audiobooks"})
                walk(new File(internal, d), 0, out);
            File[] vols = new File("/storage").listFiles();
            if (vols != null) {
                for (File v : vols) {
                    String n = v.getName();
                    if (n.equals("emulated") || n.equals("self")) continue;
                    walk(v, 0, out);
                }
            }
            Collections.sort(out, (a, c) -> a.getPath().compareToIgnoreCase(c.getPath()));
            runOnUiThread(() -> {
                tracks.clear();
                tracks.addAll(out);
                adapter.notifyDataSetChanged();
                nowSub.setText(tracks.isEmpty() ? getString(R.string.music_none)
                    : getString(R.string.music_count, tracks.size()));
                String want = getIntent().getStringExtra("path");
                if (want != null && mp == null) {
                    for (int i = 0; i < tracks.size(); i++)
                        if (tracks.get(i).getPath().equals(want)) { play(i); lv.setSelection(i); break; }
                }
            });
        }).start();
    }

    private void walk(File dir, int depth, List<File> out) {
        if (depth > 6 || out.size() >= 4000) return;
        File[] l = dir.listFiles();
        if (l == null) return;
        for (File f : l) {
            String n = f.getName();
            if (n.startsWith(".") || n.equals("Android")) continue;
            if (f.isDirectory()) walk(f, depth + 1, out);
            else if (Media.AUDIO.contains(Media.ext(n))) out.add(f);
        }
    }

    private void play(int i) {
        if (i < 0 || i >= tracks.size()) return;
        index = i;
        File f = tracks.get(i);
        try {
            if (mp != null) mp.release();
            mp = new MediaPlayer();
            mp.setDataSource(f.getPath());
            mp.setOnPreparedListener(m -> { m.start(); playBtn.setText(PAUSE); });
            mp.setOnCompletionListener(m -> next());
            mp.setOnErrorListener((m, w, e) -> {
                Toast.makeText(this, R.string.cant_open, Toast.LENGTH_SHORT).show();
                playBtn.setText(PLAY);
                return true;
            });
            mp.prepareAsync();
        } catch (Exception e) {
            Toast.makeText(this, R.string.cant_open, Toast.LENGTH_SHORT).show();
            return;
        }
        String name = f.getName();
        int dot = name.lastIndexOf('.');
        String title = dot > 0 ? name.substring(0, dot) : name;
        String sub = f.getParent();
        MediaMetadataRetriever r = new MediaMetadataRetriever();
        try {
            r.setDataSource(f.getPath());
            String t = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE);
            String a = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST);
            if (t != null && !t.isEmpty()) title = t;
            if (a != null && !a.isEmpty()) sub = a;
        } catch (Exception e) { /* keep the file name */ }
        try { r.release(); } catch (Exception e) { }
        nowTitle.setText(title);
        nowSub.setText(sub);
        progress.setProgress(0);
        adapter.notifyDataSetChanged();
    }

    private void next() {
        if (tracks.isEmpty()) return;
        play(shuffle ? rnd.nextInt(tracks.size()) : (index + 1) % tracks.size());
    }

    private void prev() {
        if (tracks.isEmpty()) return;
        play(index <= 0 ? tracks.size() - 1 : index - 1);
    }

    private void toggle() {
        if (mp == null) { play(Math.max(0, index)); return; }
        try {
            if (mp.isPlaying()) { mp.pause(); playBtn.setText(PLAY); }
            else { mp.start(); playBtn.setText(PAUSE); }
        } catch (IllegalStateException e) { /* still loading */ }
    }

    @Override public boolean onKeyDown(int code, KeyEvent e) {
        if (code == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE || code == KeyEvent.KEYCODE_MEDIA_PLAY
            || code == KeyEvent.KEYCODE_MEDIA_PAUSE) { toggle(); return true; }
        if (code == KeyEvent.KEYCODE_MEDIA_NEXT) { next(); return true; }
        if (code == KeyEvent.KEYCODE_MEDIA_PREVIOUS) { prev(); return true; }
        return super.onKeyDown(code, e);
    }

    @Override protected void onDestroy() {
        super.onDestroy();
        h.removeCallbacks(ticker);
        if (mp != null) { mp.release(); mp = null; }
    }
}
