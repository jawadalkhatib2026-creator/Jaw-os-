package dev.jaw;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Outline;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import java.util.List;

public class JawStoreActivity extends Activity {
    private static final int[] CATS = {R.string.cat_video, R.string.cat_music, R.string.cat_tools};
    // {category index, name, package id}. Apps that aren't installed open their Google Play page.
    private static final String[][] CATALOG = {
        {"0", "YouTube", "com.google.android.youtube.tv"},
        {"0", "Netflix", "com.netflix.ninja"},
        {"0", "Disney+", "com.disney.disneyplus"},
        {"0", "Prime Video", "com.amazon.amazonvideo.webapp"},
        {"0", "Shahid", "net.mbc.shahid"},
        {"0", "Discovery+", "com.discovery.discoveryplus.androidtv"},
        {"0", "Twitch", "tv.twitch.android.app"},
        {"1", "Spotify", "com.spotify.tv.android"},
        {"1", "Anghami", "com.anghami"},
        {"1", "YouTube Music", "com.google.android.youtube.tvmusic"},
        {"1", "VLC", "org.videolan.vlc"},
        {"2", "Kodi", "org.xbmc.kodi"},
        {"2", "Google Play Store", "com.android.vending"},
    };
    private static final int[][] PALETTE = {
        {0xFFE8590C, 0xFFC92A2A}, {0xFF1C7ED6, 0xFF1864AB}, {0xFF2F9E44, 0xFF1B6B30},
        {0xFF7048E8, 0xFF5F3DC4}, {0xFFD6336C, 0xFFA61E4D}, {0xFF0CA678, 0xFF087F5B},
    };

    private TextView heroName, heroSub, heroHint;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        render();
    }

    @Override protected void onResume() {
        super.onResume();
        render();
    }

    private TextView text(String s, int sp, int color) { return Ui.text(this, s, sp, color); }

    private void render() {
        LinearLayout[] pg = new LinearLayout[1];
        ScrollView sv = Ui.screen(this, getString(R.string.jaw_store), pg);
        LinearLayout page = pg[0];

        LinearLayout hero = new LinearLayout(this);
        hero.setOrientation(LinearLayout.VERTICAL);
        hero.setGravity(Gravity.BOTTOM);
        hero.setPadding(Ui.dp(this, 32), Ui.dp(this, 24), Ui.dp(this, 32), Ui.dp(this, 24));
        GradientDrawable hg = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{0xFF4C6EF5, 0xFF7048E8});
        hg.setCornerRadius(Ui.dp(this, 24));
        hero.setBackground(hg);
        heroSub = text(getString(R.string.store_featured), 16, 0xCCFFFFFF);
        heroName = text("Jaw AppStore", 38, Color.WHITE);
        heroName.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        heroHint = text("", 15, 0xB3FFFFFF);
        hero.addView(heroSub);
        hero.addView(heroName);
        hero.addView(heroHint);
        LinearLayout.LayoutParams hp = new LinearLayout.LayoutParams(-1, Ui.dp(this, 170));
        hp.bottomMargin = Ui.dp(this, 22);
        page.addView(hero, hp);

        PackageManager pm = getPackageManager();
        View first = null;
        for (int c = 0; c < CATS.length; c++) {
            TextView head = text(getString(CATS[c]), 22, Color.WHITE);
            head.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
            lp.bottomMargin = Ui.dp(this, 8);
            page.addView(head, lp);

            HorizontalScrollView hs = new HorizontalScrollView(this);
            hs.setHorizontalScrollBarEnabled(false);
            hs.setClipChildren(false);
            hs.setClipToPadding(false);
            LinearLayout row = new LinearLayout(this);
            row.setClipChildren(false);
            row.setPadding(0, Ui.dp(this, 10), 0, Ui.dp(this, 14));
            hs.addView(row);
            for (String[] app : CATALOG) {
                if (Integer.parseInt(app[0]) != c) continue;
                View t = tile(pm, app[1], app[2], CATS[c]);
                LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(Ui.dp(this, 170), Ui.dp(this, 110));
                tl.setMarginEnd(Ui.dp(this, 16));
                row.addView(t, tl);
                if (first == null) first = t;
            }
            page.addView(hs);
        }
        setContentView(sv);
        if (first != null) first.requestFocus();
    }

    private View tile(PackageManager pm, String name, String pkg, int catRes) {
        Intent li = Store.launch(pm, pkg);
        boolean installed = li != null;
        FrameLayout card = new FrameLayout(this);
        card.setFocusable(true);
        card.setClickable(true);
        int[] col = PALETTE[Math.abs(name.hashCode()) % PALETTE.length];
        GradientDrawable bg = new GradientDrawable(GradientDrawable.Orientation.TL_BR, col);
        bg.setCornerRadius(Ui.dp(this, 16));
        card.setBackground(bg);
        card.setOutlineProvider(new ViewOutlineProvider() {
            @Override public void getOutline(View v, Outline o) {
                o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), Ui.dp(JawStoreActivity.this, 16));
            }
        });
        card.setClipToOutline(true);

        Drawable icon = null;
        if (installed) {
            ActivityInfo ai = li.resolveActivityInfo(pm, 0);
            if (ai != null) icon = ai.loadIcon(pm);
        }
        if (icon != null) {
            ImageView iv = new ImageView(this);
            iv.setImageDrawable(icon);
            iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
            iv.setPadding(Ui.dp(this, 52), Ui.dp(this, 14), Ui.dp(this, 52), Ui.dp(this, 34));
            card.addView(iv, new FrameLayout.LayoutParams(-1, -1));
        } else {
            TextView ini = text(name.substring(0, 1).toUpperCase(), 40, 0xE6FFFFFF);
            ini.setGravity(Gravity.CENTER);
            ini.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
            FrameLayout.LayoutParams ip = new FrameLayout.LayoutParams(-1, -1);
            ip.bottomMargin = Ui.dp(this, 22);
            card.addView(ini, ip);
        }
        TextView label = text(name, 14, Color.WHITE);
        label.setSingleLine();
        label.setGravity(Gravity.CENTER);
        label.setBackgroundColor(0x66000000);
        label.setPadding(Ui.dp(this, 6), Ui.dp(this, 4), Ui.dp(this, 6), Ui.dp(this, 4));
        card.addView(label, new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM));
        TextView badge = text(getString(installed ? R.string.open : R.string.get), 11, Color.WHITE);
        badge.setBackground(Ui.pill(this, installed ? 0x99000000 : 0xFF3B5BDB));
        badge.setPadding(Ui.dp(this, 8), Ui.dp(this, 2), Ui.dp(this, 8), Ui.dp(this, 2));
        FrameLayout.LayoutParams bp = new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.END);
        bp.setMargins(0, Ui.dp(this, 8), Ui.dp(this, 8), 0);
        card.addView(badge, bp);

        GradientDrawable ring = new GradientDrawable();
        ring.setCornerRadius(Ui.dp(this, 16));
        ring.setColor(Color.TRANSPARENT);
        ring.setStroke(Ui.dp(this, 3), Color.WHITE);
        card.setOnFocusChangeListener((v, f) -> {
            float s = f ? 1.1f : 1f;
            v.animate().scaleX(s).scaleY(s).setDuration(150).start();
            card.setForeground(f ? ring : null);
            if (f) {
                heroName.setText(name);
                heroSub.setText(catRes);
                heroHint.setText(installed ? R.string.store_open_hint : R.string.store_get_hint);
            }
        });
        card.setOnClickListener(v -> openOrGet(pkg));
        card.setOnLongClickListener(v -> {
            List<String> l = Store.apps(this);
            if (!l.contains(pkg)) l.add(pkg);
            Store.saveApps(this, l);
            Toast.makeText(this, R.string.added_home, Toast.LENGTH_SHORT).show();
            return true;
        });
        return card;
    }

    private void openOrGet(String pkg) {
        Intent li = Store.launch(getPackageManager(), pkg);
        if (li != null) { startActivity(li); return; }
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=" + pkg))
                .setPackage("com.android.vending").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (Exception e) {
            try {
                startActivity(new Intent(Intent.ACTION_VIEW,
                    Uri.parse("https://play.google.com/store/apps/details?id=" + pkg)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            } catch (Exception e2) {
                Toast.makeText(this, R.string.cant_open, Toast.LENGTH_SHORT).show();
            }
        }
    }
}
