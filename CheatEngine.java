package com.awl.cheats;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Cheat format Atmosphere -> patch file main.patched.bin (SOS AWL).
 * Blok [Nama] = cheat biasa, {Nama} = master (selalu dipakai, juga dipakai untuk
 * memastikan file asli cocok dengan build yang benar).
 * Hanya kode tipe 0 (tulis nilai statis ke modul main) yang didukung.
 */
public final class CheatEngine {
    private CheatEngine() {}

    public static final class Write {
        public final long addr;
        public final byte[] bytes;
        Write(long addr, byte[] bytes) { this.addr = addr; this.bytes = bytes; }
    }

    public static final class Block {
        public final String name;
        public final boolean master;
        public final List<Long> words = new ArrayList<>();
        public final List<Write> writes = new ArrayList<>();
        /** null kalau semua kodenya didukung */
        public String problem;
        Block(String name, boolean master) { this.name = name; this.master = master; }
    }

    public static final class Result {
        public String error;
        public final List<String> applied = new ArrayList<>();
        public final List<String> skipped = new ArrayList<>();
    }

    private static final Pattern HEADER = Pattern.compile("^([\\[{])(.*)([\\]}])$");
    private static final Pattern HEX8 = Pattern.compile("[0-9A-Fa-f]{8}");

    public static List<Block> parse(String text) {
        if (text.startsWith("\uFEFF")) text = text.substring(1);
        List<Block> blocks = new ArrayList<>();
        Block cur = null;
        for (String raw : text.split("\\r?\\n")) {
            String line = raw.trim();
            if (line.isEmpty()) continue;
            Matcher m = HEADER.matcher(line);
            if (m.matches()) {
                cur = new Block(m.group(2).trim(), m.group(1).equals("{"));
                blocks.add(cur);
                continue;
            }
            if (cur == null) continue;
            for (String tok : line.split("\\s+")) {
                if (HEX8.matcher(tok).matches()) cur.words.add(Long.parseLong(tok, 16));
            }
        }
        for (Block b : blocks) decode(b);
        return blocks;
    }

    private static void decode(Block b) {
        List<Long> w = b.words;
        int i = 0;
        while (i < w.size()) {
            long w0 = w.get(i);
            if ((w0 >>> 28) != 0) {
                b.problem = String.format("kode %08X belum didukung (kondisi tombol/tipe lain)", w0);
                return;
            }
            int t = (int) ((w0 >>> 24) & 0xF);
            int mem = (int) ((w0 >>> 20) & 0xF);
            if (t != 1 && t != 2 && t != 4 && t != 8) {
                b.problem = String.format("lebar tulis salah di %08X", w0);
                return;
            }
            if (mem != 0) {
                b.problem = String.format("hanya tulis ke modul main yang didukung (%08X)", w0);
                return;
            }
            int n = (t == 8) ? 4 : 3;
            if (i + n > w.size()) {
                b.problem = "kode terpotong";
                return;
            }
            long addr = ((w0 & 0xFF) << 32) | w.get(i + 1);
            long val = (t == 8) ? ((w.get(i + 2) << 32) | w.get(i + 3)) : w.get(i + 2);
            byte[] bytes = new byte[t];
            for (int k = 0; k < t; k++) bytes[k] = (byte) ((val >>> (8 * k)) & 0xFF);
            b.writes.add(new Write(addr, bytes));
            i += n;
        }
    }

    private static boolean inRange(byte[] data, Write w) {
        return w.addr >= 0 && w.addr + w.bytes.length <= data.length;
    }

    /** Mengubah {@code data} di tempat (data = isi file asli). */
    public static void apply(byte[] data, List<Block> blocks, Set<String> on, Result r) {
        // semua nilai yang bisa ditulis cheat, untuk mengenali file asli yang pernah dipatch
        List<Write> cheatWrites = new ArrayList<>();
        for (Block b : blocks) if (!b.master && b.problem == null) cheatWrites.addAll(b.writes);

        List<String> bad = new ArrayList<>();
        for (Block b : blocks) {
            if (!b.master) continue;
            if (b.problem != null) { r.skipped.add("{" + b.name + "}: " + b.problem); continue; }
            for (Write w : b.writes) {
                if (!inRange(data, w)) { bad.add(String.format("main+%#x di luar file", w.addr)); continue; }
                byte[] have = Arrays.copyOfRange(data, (int) w.addr, (int) w.addr + w.bytes.length);
                if (Arrays.equals(have, w.bytes)) continue;
                boolean explained = false;
                for (Write c : cheatWrites) {
                    if (c.addr == w.addr && Arrays.equals(c.bytes, have)) { explained = true; break; }
                }
                if (!explained) bad.add(String.format("main+%#x: master mengira %s, file berisi %s",
                        w.addr, hex(w.bytes), hex(have)));
            }
        }
        if (!bad.isEmpty()) {
            StringBuilder sb = new StringBuilder("main.patched.bin.orig tidak cocok dengan master cheat. ");
            sb.append("Kemungkinan game/build berbeda atau file sudah berubah. Contoh: ");
            for (int i = 0; i < Math.min(3, bad.size()); i++) sb.append(i == 0 ? "" : "; ").append(bad.get(i));
            r.error = sb.toString();
            return;
        }
        for (Block b : blocks) {
            if (b.master && b.problem == null) for (Write w : b.writes) put(data, w);
        }
        for (Block b : blocks) {
            if (b.master || !on.contains(b.name)) continue;
            if (b.problem != null) { r.skipped.add(b.name + ": " + b.problem); continue; }
            boolean ok = true;
            for (Write w : b.writes) if (!inRange(data, w)) ok = false;
            if (!ok) { r.skipped.add(b.name + ": alamat di luar file"); continue; }
            for (Write w : b.writes) put(data, w);
            r.applied.add(b.name);
        }
    }

    private static void put(byte[] data, Write w) {
        System.arraycopy(w.bytes, 0, data, (int) w.addr, w.bytes.length);
    }

    private static String hex(byte[] b) {
        StringBuilder sb = new StringBuilder();
        for (byte x : b) sb.append(String.format("%02x", x));
        return sb.toString();
    }

    /** Menyusun mods/main.patched.bin dari mods/main.patched.bin.orig + cheat aktif. */
    public static Result runFiles(File mods, File cheatTxt, Set<String> on) throws IOException {
        Result r = new Result();
        File target = new File(mods, "main.patched.bin");
        File orig = new File(mods, "main.patched.bin.orig");
        if (!orig.exists()) {
            if (!target.exists()) { r.error = "Tidak ada main.patched.bin di " + mods; return r; }
            Files.copy(target.toPath(), orig.toPath());
        }
        String text = new String(Files.readAllBytes(cheatTxt.toPath()), StandardCharsets.UTF_8);
        List<Block> blocks = parse(text);
        byte[] data = Files.readAllBytes(orig.toPath());
        apply(data, blocks, on, r);
        if (r.error != null) return r;
        File tmp = new File(mods, "main.patched.bin.tmp");
        try (FileOutputStream o = new FileOutputStream(tmp)) {
            o.write(data);
            o.getFD().sync();
        }
        if (!tmp.renameTo(target)) {
            target.delete();
            if (!tmp.renameTo(target)) throw new IOException("Gagal mengganti main.patched.bin");
        }
        return r;
    }

    public static Set<String> readOn(File f) throws IOException {
        Set<String> s = new LinkedHashSet<>();
        if (!f.exists()) return s;
        for (String l : new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8).split("\\r?\\n")) {
            if (!l.trim().isEmpty()) s.add(l.trim());
        }
        return s;
    }

    public static void writeOn(File f, Collection<String> names) throws IOException {
        StringBuilder sb = new StringBuilder();
        for (String n : names) sb.append(n).append('\n');
        Files.write(f.toPath(), sb.toString().getBytes(StandardCharsets.UTF_8));
    }
}
