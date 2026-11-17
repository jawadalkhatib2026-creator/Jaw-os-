package dev.jaw;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.text.format.Formatter;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.MimeTypeMap;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.core.content.FileProvider;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public class FilesActivity extends Activity {
    private File current;                       // null = list of storage devices
    private final List<File> items = new ArrayList<>();
    private final List<String> labels = new ArrayList<>();
    private ArrayAdapter<String> adapter;
    private ListView lv;
    private TextView header;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setBackground(Theme.background(Store.theme(this)));
        page.setPadding(Ui.dp(this, 100), Ui.dp(this, 40), Ui.dp(this, 100), Ui.dp(this, 24));
        TextView title = Ui.text(this, getString(R.string.jaw_files), 30, Color.WHITE);
        page.addView(title);
        header = Ui.text(this, "", 16, 0xB3FFFFFF);
        LinearLayout.LayoutParams hp = new LinearLayout.LayoutParams(-2, -2);
        hp.topMargin = Ui.dp(this, 6);
        hp.bottomMargin = Ui.dp(this, 14);
        page.addView(header, hp);

        adapter = new ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, labels) {
            @Override public View getView(int p, View cv, ViewGroup parent) {
                TextView t = (TextView) super.getView(p, cv, parent);
                t.setTextColor(Color.WHITE);
                t.setTextSize(20);
                t.setSingleLine();
                t.setPadding(Ui.dp(FilesActivity.this, 20), Ui.dp(FilesActivity.this, 14),
                    Ui.dp(FilesActivity.this, 20), Ui.dp(FilesActivity.this, 14));
                return t;
            }
        };
        lv = new ListView(this);
        lv.setAdapter(adapter);
        lv.setDivider(null);
        lv.setSelector(new ColorDrawable(0x40FFFFFF));
        lv.setOnItemClickListener((p, v, pos, id) -> {
            File f = items.get(pos);
            if (f == null) goUp();
            else if (f.isDirectory()) { current = f; refresh(); }
            else openFile(f);
        });
        lv.setOnItemLongClickListener((p, v, pos, id) -> {
            File f = items.get(pos);
            if (f != null && current != null) confirmDelete(f);
            return true;
        });
        page.addView(lv, new LinearLayout.LayoutParams(-1, 0, 1f));
        setContentView(page);
        Media.ensureAccess(this);
    }

    @Override protected void onResume() {
        super.onResume();
        refresh();
    }

    private void goUp() {
        File parent = current == null ? null : current.getParentFile();
        boolean top = current == null || parent == null || !parent.canRead()
            || current.equals(Environment.getExternalStorageDirectory())
            || "/storage".equals(parent.getPath());
        current = top ? null : parent;
        refresh();
    }

    @Override public void onBackPressed() {
        if (current != null) goUp(); else super.onBackPressed();
    }

    private void refresh() {
        labels.clear();
        items.clear();
        if (current == null) {
            header.setText(R.string.files_roots);
            labels.add("\u25B8  " + getString(R.string.files_internal));
            items.add(Environment.getExternalStorageDirectory());
            File[] vols = new File("/storage").listFiles();
            if (vols != null) {
                for (File v : vols) {
                    String n = v.getName();
                    if (n.equals("emulated") || n.equals("self") || !v.canRead()) continue;
                    labels.add("\u25B8  " + getString(R.string.files_usb, n));
                    items.add(v);
                }
            }
        } else {
            header.setText(current.getPath());
            labels.add("\u2B06  " + getString(R.string.files_up));
            items.add(null);
            File[] l = current.listFiles();
            if (l == null || l.length == 0) {
                Toast.makeText(this, R.string.files_empty, Toast.LENGTH_SHORT).show();
            } else {
                Arrays.sort(l, (a, c) -> {
                    if (a.isDirectory() != c.isDirectory()) return a.isDirectory() ? -1 : 1;
                    return a.getName().compareToIgnoreCase(c.getName());
                });
                for (File f : l) {
                    labels.add(prefix(f) + f.getName()
                        + (f.isDirectory() ? "" : "     " + Formatter.formatShortFileSize(this, f.length())));
                    items.add(f);
                }
            }
        }
        adapter.notifyDataSetChanged();
        lv.setSelection(0);
        lv.requestFocus();
    }

    private static String prefix(File f) {
        if (f.isDirectory()) return "\u25B8  ";
        String e = Media.ext(f.getName());
        if (Media.AUDIO.contains(e)) return "\u266A  ";
        if (Media.IMAGE.contains(e)) return "\u25A3  ";
        if (Media.VIDEO.contains(e)) return "\u25B6  ";
        return "\u2022  ";
    }

    private void openFile(File f) {
        String e = Media.ext(f.getName());
        if (Media.AUDIO.contains(e)) {
            startActivity(new Intent(this, MusicActivity.class).putExtra("path", f.getPath()));
        } else if (Media.IMAGE.contains(e) || Media.VIDEO.contains(e)) {
            startActivity(new Intent(this, ViewActivity.class).putExtra("path", f.getPath()));
        } else {
            try {
                String mime = e.equals("apk") ? "application/vnd.android.package-archive"
                    : MimeTypeMap.getSingleton().getMimeTypeFromExtension(e);
                Uri u = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", f);
                startActivity(new Intent(Intent.ACTION_VIEW).setDataAndType(u, mime == null ? "*/*" : mime)
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION));
            } catch (Exception ex) {
                Toast.makeText(this, R.string.cant_open, Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void confirmDelete(File f) {
        new AlertDialog.Builder(this).setMessage(getString(R.string.files_delete_q, f.getName()))
            .setPositiveButton(R.string.ok, (d, w) -> {
                f.delete();
                Toast.makeText(this, R.string.deleted, Toast.LENGTH_SHORT).show();
                refresh();
            })
            .setNegativeButton(R.string.cancel, null).show();
    }
}
