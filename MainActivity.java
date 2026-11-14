package com.awl.cheats;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class MainActivity extends Activity {
    private static final File BASE = new File("/storage/emulated/0/SOS AWL");
    private final File modsDir = new File(BASE, "mods");
    private final File cheatsDir = new File(BASE, "cheats");
    private final File cheatFile = new File(cheatsDir, "cheat.txt");
    private final File onFile = new File(cheatsDir, "cheats_on.txt");

    private LinearLayout list;
    private TextView status;
    private Button btnApply;
    private Button btnPerm;
    private final List<Switch> switches = new ArrayList<>();
    private final List<String> names = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        int pad = (int) (16 * getResources().getDisplayMetrics().density);

        ScrollView sv = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(pad, pad * 2, pad, pad);
        sv.addView(root);

        TextView title = new TextView(this);
        title.setText("AWL Cheats");
        title.setTextSize(24);
        root.addView(title);

        TextView hint = new TextView(this);
        hint.setText("Nyalakan cheat, tekan Terapkan, lalu tutup game sepenuhnya dan buka lagi.");
        hint.setPadding(0, pad / 2, 0, pad);
        root.addView(hint);

        btnPerm = new Button(this);
        btnPerm.setText("Beri izin akses semua file");
        btnPerm.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { openPermissionSettings(); }
        });
        root.addView(btnPerm);

        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        root.addView(list);

        btnApply = new Button(this);
        btnApply.setText("Terapkan");
        btnApply.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { applyNow(); }
        });
        root.addView(btnApply);

        status = new TextView(this);
        status.setPadding(0, pad, 0, 0);
        root.addView(status);

        setContentView(sv);
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
    }

    private boolean hasAccess() {
        return Environment.isExternalStorageManager();
    }

    private void openPermissionSettings() {
        Intent i = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                Uri.parse("package:" + getPackageName()));
        try {
            startActivity(i);
        } catch (Exception e) {
            startActivity(new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION));
        }
    }

    private void ensureCheatFile() throws IOException {
        if (cheatFile.exists()) return;
        cheatsDir.mkdirs();
        InputStream in = getAssets().open("cheat.txt");
        try (FileOutputStream out = new FileOutputStream(cheatFile)) {
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
        } finally {
            in.close();
        }
        if (!onFile.exists()) {
            Set<String> def = new LinkedHashSet<>();
            def.add("60 FPS");
            CheatEngine.writeOn(onFile, def);
        }
    }

    private void refresh() {
        list.removeAllViews();
        switches.clear();
        names.clear();
        if (!hasAccess()) {
            btnPerm.setVisibility(View.VISIBLE);
            btnApply.setEnabled(false);
            status.setText("Izin 'Akses semua file' dibutuhkan untuk mengubah folder SOS AWL.");
            return;
        }
        btnPerm.setVisibility(View.GONE);
        btnApply.setEnabled(true);
        try {
            ensureCheatFile();
            String text = new String(java.nio.file.Files.readAllBytes(cheatFile.toPath()), StandardCharsets.UTF_8);
            List<CheatEngine.Block> blocks = CheatEngine.parse(text);
            Set<String> on = CheatEngine.readOn(onFile);
            for (CheatEngine.Block b : blocks) {
                if (b.master) continue;
                Switch s = new Switch(this);
                s.setText(b.problem == null ? b.name : b.name + "  (belum didukung)");
                s.setEnabled(b.problem == null);
                s.setChecked(on.contains(b.name));
                s.setPadding(0, 24, 0, 24);
                list.addView(s);
                switches.add(s);
                names.add(b.name);
            }
            boolean haveFile = new File(modsDir, "main.patched.bin").exists();
            status.setText(haveFile ? "Siap." : "main.patched.bin tidak ditemukan di " + modsDir);
        } catch (Exception e) {
            status.setText("Error: " + e);
        }
    }

    private void applyNow() {
        final Set<String> on = new LinkedHashSet<>();
        for (int i = 0; i < switches.size(); i++) {
            if (switches.get(i).isChecked() && switches.get(i).isEnabled()) on.add(names.get(i));
        }
        btnApply.setEnabled(false);
        status.setText("Menyusun...");
        new Thread(new Runnable() {
            @Override public void run() {
                String msg;
                try {
                    CheatEngine.writeOn(onFile, on);
                    CheatEngine.Result r = CheatEngine.runFiles(modsDir, cheatFile, on);
                    if (r.error != null) {
                        msg = "Gagal: " + r.error;
                    } else {
                        StringBuilder sb = new StringBuilder("Selesai. Aktif (" + r.applied.size() + "): ");
                        sb.append(r.applied.isEmpty() ? "-" : join(r.applied));
                        for (String s : r.skipped) sb.append("\nDilewati ").append(s);
                        sb.append("\nTutup game sepenuhnya, lalu buka lagi.");
                        msg = sb.toString();
                    }
                } catch (Throwable t) {
                    msg = "Error: " + t;
                }
                final String m = msg;
                runOnUiThread(new Runnable() {
                    @Override public void run() {
                        status.setText(m);
                        btnApply.setEnabled(true);
                    }
                });
            }
        }).start();
    }

    private static String join(List<String> l) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < l.size(); i++) sb.append(i == 0 ? "" : ", ").append(l.get(i));
        return sb.toString();
    }
}
