# AWL Cheats

Aplikasi kecil untuk menyalakan/mematikan cheat di port SOS AWL tanpa Termux.

## Cara pakai
1. Pasang APK, buka, lalu beri izin "Akses semua file".
2. Pastikan `SOS AWL/mods/main.patched.bin` ada. Pertama kali dijalankan, aplikasi menyimpan salinan
   `main.patched.bin.orig` (ideal: file asli sebelum dipatch; kalau sudah 60 fps juga tidak apa-apa).
3. Nyalakan cheat yang diinginkan, tekan **Terapkan**.
4. Tutup game sepenuhnya, lalu buka lagi.

Cheat dibaca dari `SOS AWL/cheats/cheat.txt` (format Atmosphere; `{Master}` = blok master, `[Nama]` = cheat).
Mau menghapus/menambah cheat: edit file itu, buka lagi aplikasinya. Status aktif ada di `cheats_on.txt`.
Hanya kode "tulis nilai statis" (tipe 0, modul main) yang didukung; kode berkondisi tombol ditandai "belum didukung".

## Membangun APK (tanpa memasang apa pun di laptop)
1. Buat repo GitHub baru, unggah semua isi folder ini (termasuk folder `.github`).
2. Buka tab **Actions**, jalankan workflow **build-apk** (atau cukup push).
3. Unduh artifact `AWLCheats-apk` -> `app-debug.apk`.

## Atau lewat Android Studio
Buka folder ini, lalu Build > Build APK(s).

## Catatan
- Kode belum pernah dibangun dengan Android Gradle Plugin; mesin cheat-nya (`CheatEngine.java`) sudah diuji
  terhadap `main.patched.bin` asli, tetapi tampilan (`MainActivity.java`) belum diuji di perangkat.
- Backup folder save sebelum mencoba cheat.
