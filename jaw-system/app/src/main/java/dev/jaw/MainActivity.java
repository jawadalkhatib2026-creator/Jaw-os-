package dev.jaw;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Outline;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.animation.OvershootInterpolator;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextClock;
import android.widget.TextView;
import android.widget.Toast;
import java.io.File;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;

public class MainActivity extends Activity {
    private static boolean introShown = false;
    private static final int[][] PALETTE = {
        {0xFFE8590C, 0xFFC92A2A}, {0xFF1C7ED6, 0xFF1864AB}, {0xFF2F9E44, 0xFF1B6B30},
        {0xFF7048E8, 0xFF5F3DC4}, {0xFFD6336C, 0xFFA61E4D}, {0xFF0CA678, 0xFF087F5B},
    };
    private static final int[] GREY = {0xFF3A3A3C, 0xFF2C2C2E};

    private FrameLayout root;
    private ImageView bg;
    private View dim;
    private LinearLayout bar, quick;
    private TextView heroTitle;
    private TextClock clock, date;
    private View gear;
    private Bitmap wallBitmap;
    private long wallStamp = -1;

    private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }

    private TextView text(String s, int sp, int color) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        return t;
    }

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        root = new FrameLayout(this);
        root.setClipChildren(false);

        bg = new ImageView(this);
        bg.setScaleType(ImageView.ScaleType.CENTER_CROP);
        root.addView(bg, new FrameLayout.LayoutParams(-1, -1));
        dim = new View(this);
        dim.setBackgroundColor(0x55000000);
        root.addView(dim, new FrameLayout.LayoutParams(-1, -1));

        // logo + name, top left
        LinearLayout brand = new LinearLayout(this);
        brand.setGravity(Gravity.CENTER_VERTICAL);
        ImageView logo = new ImageView(this);
        logo.setImageResource(R.drawable.ic_logo);
        brand.addView(logo, new LinearLayout.LayoutParams(dp(44), dp(44)));
        TextView title = text(getString(R.string.app_name), 26, 0xE6FFFFFF);
        title.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(-2, -2);
        tl.setMarginStart(dp(14));
        brand.addView(title, tl);
        FrameLayout.LayoutParams bp = new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.START);
        bp.setMarginStart(dp(56));
        bp.topMargin = dp(40);
        root.addView(brand, bp);

        // clock + date, top right
        LinearLayout clockBox = new LinearLayout(this);
        clockBox.setOrientation(LinearLayout.VERTICAL);
        clockBox.setGravity(Gravity.END);
        clock = new TextClock(this);
        clock.setTextSize(44);
        clock.setTextColor(Color.WHITE);
        clock.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        date = new TextClock(this);
        date.setTextSize(20);
        date.setTextColor(0xB3FFFFFF);
        date.setFormat12Hour("EEEE, d MMMM");
        date.setFormat24Hour("EEEE, d MMMM");
        clockBox.addView(clock);
        clockBox.addView(date);
        FrameLayout.LayoutParams cp = new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.END);
        cp.setMarginEnd(dp(56));
        cp.topMargin = dp(24);
        root.addView(clockBox, cp);

        // bottom: hero title, quick settings row, app bar
        LinearLayout bottom = new LinearLayout(this);
        bottom.setOrientation(LinearLayout.VERTICAL);
        bottom.setClipChildren(false);
        heroTitle = text("", 28, Color.WHITE);
        heroTitle.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        LinearLayout.LayoutParams hp = new LinearLayout.LayoutParams(-2, -2);
        hp.setMarginStart(dp(56));
        hp.bottomMargin = dp(10);
        bottom.addView(heroTitle, hp);

        quick = new LinearLayout(this);
        quick.setGravity(Gravity.CENTER_VERTICAL);
        quick.setPadding(dp(56), 0, dp(56), 0);
        quick.setClipChildren(false);
        quick.setVisibility(View.INVISIBLE);
        bottom.addView(quick, new LinearLayout.LayoutParams(-1, dp(92)));

        HorizontalScrollView sv = new HorizontalScrollView(this);
        sv.setHorizontalScrollBarEnabled(false);
        sv.setClipToPadding(false);
        sv.setClipChildren(false);
        sv.setPadding(dp(56), 0, dp(56), 0);
        sv.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
            new int[]{0xAA0A1020, 0xF00A1020}));
        bar = new LinearLayout(this);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setClipChildren(false);
        sv.addView(bar, new FrameLayout.LayoutParams(-2, -1));
        bottom.addView(sv, new LinearLayout.LayoutParams(-1, dp(112)));
        root.addView(bottom, new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM));

        buildQuick();
        setContentView(root);

        root.getViewTreeObserver().addOnGlobalFocusChangeListener((oldF, newF) -> {
            if (newF == null) return;
            Object tag = newF.getTag();
            if (tag instanceof String) heroTitle.setText((String) tag);
            quick.setVisibility(newF == gear || isInside(quick, newF) ? View.VISIBLE : View.INVISIBLE);
        });

        if (!introShown && Store.setupDone(this)) playIntro();
        introShown = true;
    }

    private static boolean isInside(View parent, View v) {
        for (View c = v; c != null; ) {
            if (c == parent) return true;
            android.view.ViewParent p = c.getParent();
            c = p instanceof View ? (View) p : null;
        }
        return false;
    }

    @Override protected void onResume() {
        super.onResume();
        if (!Store.setupDone(this)) {
            startActivity(new Intent(this, SetupActivity.class));
            return;
        }
        root.setBackground(Theme.background(Store.theme(this)));
        applyWallpaper();
        String f = Store.h24(this) ? "HH:mm" : "h:mm a";
        clock.setFormat12Hour(f);
        clock.setFormat24Hour(f);
        populate();
    }

    @Override public void onBackPressed() { /* a launcher has nowhere to go back to */ }

    private void applyWallpaper() {
        File w = Store.wallpaperFile(this);
        if (w.exists()) {
            if (wallBitmap == null || wallStamp != w.lastModified()) {
                wallBitmap = BitmapFactory.decodeFile(w.getPath());
                wallStamp = w.lastModified();
            }
            bg.setImageBitmap(wallBitmap);
            if (android.os.Build.VERSION.SDK_INT >= 31) {
                bg.setRenderEffect(Store.glass(this)
                    ? android.graphics.RenderEffect.createBlurEffect(14f, 14f, android.graphics.Shader.TileMode.CLAMP)
                    : null);
            }
            bg.setVisibility(View.VISIBLE);
            dim.setVisibility(View.VISIBLE);
        } else {
            wallBitmap = null;
            wallStamp = -1;
            bg.setImageDrawable(null);
            bg.setVisibility(View.GONE);
            dim.setVisibility(View.GONE);
        }
    }

    // ---- quick settings row (shown above the bar when the gear is selected) ----

    private void buildQuick() {
        quick.removeAllViews();
        addQuick("✦", getString(R.string.jaw_settings),
            v -> startActivity(new Intent(this, JawSettingsActivity.class)));
        addQuick("⚙", getString(R.string.original_settings), v -> Sys.tvSettings(this));
        addQuick("⟳", getString(R.string.update_check), v -> Updater.check(this));
        addQuick("⌂", getString(R.string.home_short), v -> Sys.homeSettings(this));
    }

    private void addQuick(String glyph, String label, View.OnClickListener click) {
        LinearLayout c = new LinearLayout(this);
        c.setGravity(Gravity.CENTER);
        c.setPadding(dp(16), 0, dp(16), 0);
        c.addView(text(glyph, 22, Color.WHITE));
        TextView t = text(label, 16, Color.WHITE);
        t.setSingleLine();
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
        lp.setMarginStart(dp(12));
        c.addView(t, lp);
        FrameLayout card = tile(label, c, GREY, click);
        LinearLayout.LayoutParams cl = new LinearLayout.LayoutParams(dp(200), dp(60));
        cl.setMarginEnd(dp(18));
        quick.addView(card, cl);
    }

    // ---- app bar ----

    private void addBar(View v, int w, int h) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(w, h);
        lp.setMarginEnd(dp(14));
        bar.addView(v, lp);
    }

    private View iconButton(String glyph, String label, View.OnClickListener click) {
        TextView g = text(glyph, 20, Color.WHITE);
        g.setGravity(Gravity.CENTER);
        View b = tile(label, g, GREY, click);
        addBar(b, dp(52), dp(52));
        return b;
    }

    private void populate() {
        bar.removeAllViews();
        gear = iconButton("⚙", getString(R.string.settings_label), v -> {
            View q = quick.getChildAt(0);
            if (q != null) { quick.setVisibility(View.VISIBLE); q.requestFocus(); }
        });
        iconButton("↓", getString(R.string.jaw_store), v -> startActivity(new Intent(this, JawStoreActivity.class)));
        iconButton("☺", getString(R.string.jaw_accounts), v -> startActivity(new Intent(this, AccountsActivity.class)));
        iconButton("⇄", getString(R.string.jaw_share), v -> startActivity(new Intent(this, ShareActivity.class)));
        iconButton("\u25A3", getString(R.string.jaw_gallery), v -> startActivity(new Intent(this, GalleryActivity.class)));
        iconButton("\u25A4", getString(R.string.jaw_files), v -> startActivity(new Intent(this, FilesActivity.class)));
        iconButton("\u266B", getString(R.string.jaw_music), v -> startActivity(new Intent(this, MusicActivity.class)));
        View sep = new View(this);
        sep.setBackgroundColor(0x55FFFFFF);
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(dp(2), dp(44));
        sp.setMarginStart(dp(4));
        sp.setMarginEnd(dp(18));
        bar.addView(sep, sp);

        View firstApp = null;
        PackageManager pm = getPackageManager();
        for (String pkg : Store.apps(this)) {
            Intent li = Store.launch(pm, pkg);
            if (li == null) continue;
            View t = appTile(pm, pkg, li);
            addBar(t, dp(118), dp(76));
            if (firstApp == null) firstApp = t;
        }
        JSONArray chans = Store.channels(this);
        for (int i = 0; i < chans.length(); i++) {
            JSONObject o = chans.optJSONObject(i);
            if (o == null) continue;
            View t = channelTile(o);
            addBar(t, dp(118), dp(76));
            if (firstApp == null) firstApp = t;
        }
        (firstApp != null ? firstApp : gear).requestFocus();
    }

    private View appTile(PackageManager pm, String pkg, Intent li) {
        CharSequence label = pkg;
        try { label = pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)); } catch (Exception e) { }
        ImageView iv = new ImageView(this);
        Drawable d = null;
        boolean banner = false;
        ActivityInfo ai = li.resolveActivityInfo(pm, 0);
        if (ai != null) {
            d = ai.loadBanner(pm);
            if (d != null) banner = true; else d = ai.loadIcon(pm);
        }
        if (banner) {
            iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
        } else {
            iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
            iv.setPadding(dp(24), dp(10), dp(24), dp(10));
        }
        iv.setImageDrawable(d);
        FrameLayout t = tile(label.toString(), iv, new int[]{0xFF2C2C2E, 0xFF1C1C1E}, v -> startActivity(li));
        t.setOnLongClickListener(v -> {
            List<String> l = Store.apps(this);
            l.remove(pkg);
            Store.saveApps(this, l);
            Toast.makeText(this, R.string.removed, Toast.LENGTH_SHORT).show();
            populate();
            return true;
        });
        return t;
    }

    private View channelTile(JSONObject o) {
        final String id = o.optString("id");
        final String name = o.optString("name");
        final String pkg = o.optString("pkg");
        final String url = o.optString("url");
        View content;
        File logo = Store.logoFile(this, id);
        Bitmap bmp = logo.exists() ? BitmapFactory.decodeFile(logo.getPath()) : null;
        if (bmp != null) {
            ImageView iv = new ImageView(this);
            iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
            iv.setImageBitmap(bmp);
            content = iv;
        } else {
            String initials = name.length() >= 2 ? name.substring(0, 2) : name;
            TextView g = text(initials.toUpperCase(), 24, Color.WHITE);
            g.setGravity(Gravity.CENTER);
            g.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
            content = g;
        }
        int[] colors = PALETTE[Math.abs(name.hashCode()) % PALETTE.length];
        FrameLayout t = tile(name, content, colors, v -> openChannel(pkg, url));
        t.setOnLongClickListener(v -> {
            Store.removeChannel(this, id);
            Toast.makeText(this, R.string.removed, Toast.LENGTH_SHORT).show();
            populate();
            return true;
        });
        return t;
    }

    private void openChannel(String pkg, String url) {
        if (url != null && !url.isEmpty()) {
            try {
                Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                if (pkg != null && !pkg.isEmpty()) i.setPackage(pkg);
                startActivity(i);
                return;
            } catch (Exception e) { /* fall back to just opening the app */ }
        }
        Intent li = pkg == null || pkg.isEmpty() ? null : Store.launch(getPackageManager(), pkg);
        if (li != null) startActivity(li);
        else Toast.makeText(this, R.string.cant_open, Toast.LENGTH_SHORT).show();
    }

    /** One selectable card. Grows and gets a white ring when focused; its tag is the name shown above the bar. */
    private FrameLayout tile(String label, View content, int[] colors, View.OnClickListener click) {
        FrameLayout card = new FrameLayout(this);
        card.setFocusable(true);
        card.setClickable(true);
        card.setTag(label);
        if (Store.glass(this)) {
            GradientDrawable gd = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0x66FFFFFF, 0x22FFFFFF});
            gd.setCornerRadius(dp(14));
            gd.setStroke(dp(1), 0x99FFFFFF);
            card.setBackground(gd);
        } else {
            GradientDrawable gd = new GradientDrawable(GradientDrawable.Orientation.TL_BR, colors);
            gd.setCornerRadius(dp(14));
            card.setBackground(gd);
        }
        card.setOutlineProvider(new ViewOutlineProvider() {
            @Override public void getOutline(View v, Outline o) {
                o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), dp(14));
            }
        });
        card.setClipToOutline(true);
        card.addView(content, new FrameLayout.LayoutParams(-1, -1));

        GradientDrawable ring = new GradientDrawable();
        ring.setCornerRadius(dp(14));
        ring.setColor(Color.TRANSPARENT);
        ring.setStroke(dp(3), Color.WHITE);
        card.setOnFocusChangeListener((v, focused) -> {
            float s = focused ? 1.14f : 1f;
            v.animate().scaleX(s).scaleY(s).setDuration(150).start();
            card.setForeground(focused ? ring : null);
        });
        card.setOnClickListener(click);
        return card;
    }

    // ---- start-up animation: the logo walks in, bounces, then the name appears ----

    private void playIntro() {
        FrameLayout ov = new FrameLayout(this);
        ov.setBackgroundColor(0xFF05070F);
        ov.setClickable(true);
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setGravity(Gravity.CENTER);
        ImageView logo = new ImageView(this);
        logo.setImageResource(R.drawable.ic_logo);
        col.addView(logo, new LinearLayout.LayoutParams(dp(150), dp(150)));
        TextView name = text(getString(R.string.app_name), 40, Color.WHITE);
        name.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        name.setAlpha(0f);
        LinearLayout.LayoutParams np = new LinearLayout.LayoutParams(-2, -2);
        np.topMargin = dp(24);
        col.addView(name, np);
        ov.addView(col, new FrameLayout.LayoutParams(-1, -1));
        root.addView(ov, new FrameLayout.LayoutParams(-1, -1));

        float off = -dp(900);
        logo.setTranslationX(off);
        ObjectAnimator slide = ObjectAnimator.ofFloat(logo, "translationX", off, 0f);
        slide.setDuration(1400);
        slide.setInterpolator(new OvershootInterpolator(1.1f));
        ObjectAnimator wob = ObjectAnimator.ofFloat(logo, "rotation", -9f, 9f);
        wob.setDuration(175);
        wob.setRepeatCount(7);
        wob.setRepeatMode(ValueAnimator.REVERSE);
        ObjectAnimator hop = ObjectAnimator.ofFloat(logo, "translationY", 0f, -dp(16));
        hop.setDuration(175);
        hop.setRepeatCount(7);
        hop.setRepeatMode(ValueAnimator.REVERSE);
        AnimatorSet walk = new AnimatorSet();
        walk.playTogether(slide, wob, hop);
        walk.addListener(new AnimatorListenerAdapter() {
            @Override public void onAnimationEnd(Animator a) {
                logo.setRotation(0f);
                logo.setTranslationX(0f);
                logo.setTranslationY(0f);
                logo.animate().scaleX(1.18f).scaleY(1.18f).setDuration(260)
                    .withEndAction(() -> logo.animate().scaleX(1f).scaleY(1f).setDuration(260).start()).start();
                name.setTranslationY(dp(20));
                name.animate().alpha(1f).translationY(0f).setDuration(500).start();
                ov.postDelayed(() -> ov.animate().alpha(0f).setDuration(500)
                    .withEndAction(() -> root.removeView(ov)).start(), 1300);
            }
        });
        walk.start();
    }
}
