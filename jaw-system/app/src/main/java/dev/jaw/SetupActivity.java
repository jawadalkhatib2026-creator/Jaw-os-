package dev.jaw;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Toast;
import java.io.FileOutputStream;
import java.util.Locale;

/** First-run wizard: welcome, Jaw account, look. */
public class SetupActivity extends Activity {
    private int step = 0;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        render();
    }

    @Override public void onBackPressed() {
        if (step > 0) { step--; render(); }
    }

    private void next() { step++; render(); }

    private void render() {
        int[] titles = {R.string.setup_welcome, R.string.setup_account_title, R.string.setup_look, R.string.setup_finish};
        LinearLayout[] pg = new LinearLayout[1];
        ScrollView sv = Ui.screen(this, getString(titles[step]), pg);
        LinearLayout p = pg[0];
        View first;
        LinearLayout.LayoutParams gap = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        gap.bottomMargin = Ui.dp(this, 24);
        if (step == 0) {
            p.addView(Ui.text(this, getString(R.string.setup_intro), 20, 0xCCFFFFFF), gap);
            first = Ui.row(this, p, getString(R.string.setup_start), null, v -> next());
        } else if (step == 1) {
            first = Ui.row(this, p, getString(R.string.create_here), null, v -> createAccount());
            Ui.row(this, p, getString(R.string.setup_skip), null, v -> next());
        } else if (step == 2) {
            first = Ui.row(this, p, getString(R.string.theme), getString(Theme.NAMES[Store.theme(this)]), v -> {
                Store.setTheme(this, (Store.theme(this) + 1) % Theme.GRADIENTS.length);
                render();
            });
            Ui.row(this, p, getString(R.string.wp_presets), null, v -> presets());
            Ui.row(this, p, getString(R.string.setup_continue), null, v -> next());
        } else {
            p.addView(Ui.text(this, getString(R.string.setup_done_msg), 20, 0xCCFFFFFF), gap);
            first = Ui.row(this, p, getString(R.string.setup_finish), null, v -> finishSetup());
        }
        setContentView(sv);
        first.requestFocus();
    }

    private void createAccount() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(Ui.dp(this, 24), Ui.dp(this, 16), Ui.dp(this, 24), 0);
        EditText e = new EditText(this);
        e.setHint(R.string.email);
        e.setSingleLine();
        e.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
        EditText pw = new EditText(this);
        pw.setHint(R.string.password);
        pw.setSingleLine();
        pw.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        box.addView(e);
        box.addView(pw);
        new AlertDialog.Builder(this).setTitle(R.string.create_here).setView(box)
            .setPositiveButton(R.string.ok, (d, w) -> {
                String err = Accounts.create(this, e.getText().toString(), pw.getText().toString());
                if (err != null) { Toast.makeText(this, err, Toast.LENGTH_LONG).show(); return; }
                Store.setUser(this, e.getText().toString().trim().toLowerCase(Locale.US));
                Toast.makeText(this, R.string.acct_created, Toast.LENGTH_SHORT).show();
                next();
            })
            .setNegativeButton(R.string.cancel, null).show();
    }

    private void presets() {
        String[] names = new String[Presets.NAMES.length];
        for (int i = 0; i < names.length; i++) names[i] = getString(Presets.NAMES[i]);
        new AlertDialog.Builder(this).setTitle(R.string.wp_presets)
            .setItems(names, (d, which) -> {
                Bitmap bm = Presets.render(which);
                try (FileOutputStream o = new FileOutputStream(Store.wallpaperFile(this))) {
                    bm.compress(Bitmap.CompressFormat.JPEG, 92, o);
                    Toast.makeText(this, R.string.wallpaper_set, Toast.LENGTH_SHORT).show();
                } catch (Exception ex) {
                    Toast.makeText(this, R.string.cant_open, Toast.LENGTH_SHORT).show();
                }
            })
            .setNegativeButton(R.string.cancel, null).show();
    }

    private void finishSetup() {
        Store.setSetupDone(this, true);
        startActivity(new Intent(this, MainActivity.class));
        finish();
    }
}
