package com.awl.cheats;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class MainActivity extends Activity {
    private static final File STORAGE = new File("/storage/emulated/0");
    private static final String BUNDLED_GAME = "SOS AWL"; // cheat.txt bawaan hanya untuk folder ini

    private LinearLayout list;
    private TextView status;
    private Button btnApply;
    private Button btnPerm;
    private Spinner spinner;
    private TextView gameLabel;
    private final List<Switch> switches = new ArrayList<>();
    private final List<String> names = new ArrayList<>();
    private final List<File> games = new ArrayList<>();
    private File base = null;
    private boolean spinnerReady = false;

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
        hint.setText("Pilih game, nyalakan cheat, tekan Terapkan, lalu tutup game sepenuhnya dan buka lagi.");
        hint.setPadding(0, pad / 2, 0, pad);
        root.addView(hint);

        btnPerm = new Button(this);
        btnPerm.setText("Beri izin akses semua file");
        btnPerm.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { openPermissionSettings(); }
        });
        root.addView(btnPerm);

        gameLabel = new TextView(this);
        gameLabel.setText("Game:");
        root.addView(gameLabel);

        spinner = new Spinner(this);
        root.addView(spinner);

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
        if (!hasAccess()) {
            showNoAccess();
            return;
        }
        btnPerm.setVisibility(View.GONE);
        loadGames();
    }

    private boolean hasAccess() {
        return Environment.isExternalStorageManager();
    }

    private void showNoAccess() {
        list.removeAllViews();
        switches.clear();
        names.clear();
        btnPerm.setVisibility(View.VISIBLE);
        btnApply.setEnabled(false);
        status.setText("Izin 'Akses semua file' dibutuhkan untuk mengubah folder game.");
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

    /** Folder game = folder di penyimpanan utama yang punya mods/main.patched.bin(.orig). */
    private void loadGames() {
        games.clear();
        File[] dirs = STORAGE.listFiles();
        if (dirs != null) {
            Arrays.sort(dirs);
            for (File d : dirs) {
                if (!d.isDirectory()) continue;
                if (new File(d, "mods/main.patched.bin").exists() || new File(d, "mods/main.patched.bin.orig").exists()) {
                    games.add(d);
                }
            }
        }
        spinnerReady = false;
        List<String> labels = new ArrayList<>();
        for (File g : games) labels.add(g.getName());
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(this, android.R.layout.simple_spinner_item, labels);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);

        if (games.isEmpty()) {
            base = null;
            list.removeAllViews();
            switches.clear();
            names.clear();
            btnApply.setEnabled(false);
            status.setText("Tidak ada folder game dengan mods/main.patched.bin di " + STORAGE + ".");
            return;
        }
        SharedPreferences sp = getSharedPreferences("awl", MODE_PRIVATE);
        String last = sp.getString("game", BUNDLED_GAME);
        int sel = 0;
        for (int i = 0; i < games.size(); i++) if (games.get(i).getName().equals(last)) sel = i;
        spinner.setSelection(sel);
        base = games.get(sel);
        spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (!spinnerReady) { spinnerReady = true; return; }
                base = games.get(position);
                getSharedPreferences("awl", MODE_PRIVATE).edit().putString("game", base.getName()).apply();
                refresh();
            }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });
        refresh();
    }

    private File modsDir()   { return new File(base, "mods"); }
    private File cheatsDir() { return new File(base, "cheats"); }
    private File cheatFile() { return new File(cheatsDir(), "cheat.txt"); }
    private File onFile()    { return new File(cheatsDir(), "cheats_on.txt"); }

    private void ensureCheatFile() throws IOException {
        if (cheatFile().exists()) return;
        if (!base.getName().equals(BUNDLED_GAME)) return;
        cheatsDir().mkdirs();
        InputStream in = getAssets().open("cheat.txt");
        try (FileOutputStream out = new FileOutputStream(cheatFile())) {
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
        } finally {
            in.close();
        }
        if (!onFile().exists()) {
            Set<String> def = new LinkedHashSet<>();
            def.add("60 FPS");
            CheatEngine.writeOn(onFile(), def);
        }
    }

    private void refresh() {
        list.removeAllViews();
        switches.clear();
        names.clear();
        if (base == null) return;
        try {
            ensureCheatFile();
            if (!cheatFile().exists()) {
                btnApply.setEnabled(false);
                status.setText("Belum ada cheat.txt (format Atmosphere) di " + cheatsDir()
                        + ". Taruh file itu di sana, lalu buka lagi aplikasinya.");
                return;
            }
            btnApply.setEnabled(true);
            String text = new String(java.nio.file.Files.readAllBytes(cheatFile().toPath()), StandardCharsets.UTF_8);
            List<CheatEngine.Block> blocks = CheatEngine.parse(text);
            Set<String> on = CheatEngine.readOn(onFile());
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
            boolean haveFile = new File(modsDir(), "main.patched.bin").exists();
            status.setText(haveFile ? "Siap." : "main.patched.bin tidak ditemukan di " + modsDir());
        } catch (Exception e) {
            status.setText("Error: " + e);
        }
    }

    private void applyNow() {
        if (base == null) return;
        final File mods = modsDir();
        final File cheat = cheatFile();
        final File onF = onFile();
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
                    CheatEngine.writeOn(onF, on);
                    CheatEngine.Result r = CheatEngine.runFiles(mods, cheat, on);
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
