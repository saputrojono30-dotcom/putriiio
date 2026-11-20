# Membangun APK DLMS-S3 Control

Proyek ini berdiri sendiri (tanpa firmware). Pilih salah satu cara:

## A. Android Studio (paling mudah)
1. Pasang Android Studio (Koala atau lebih baru).
2. File ▸ Open ▸ pilih folder ini, tunggu Gradle sync (butuh internet sekali).
3. Build ▸ Build Bundle(s)/APK(s) ▸ Build APK(s).
4. Hasil: `app/build/outputs/apk/debug/app-debug.apk` — kirim ke HP dan pasang
   (izinkan "pasang dari sumber tidak dikenal").
   Atau colok HP (USB debugging) lalu tekan Run.

## B. Tanpa Android Studio, lewat GitHub (build di cloud)
1. Buat repositori GitHub baru, unggah seluruh isi folder ini (termasuk `.github`).
2. Tab Actions ▸ "Build APK" ▸ Run workflow.
3. Setelah hijau, unduh artifact `DLMS-S3-Control-debug-apk` berisi `app-debug.apk`.

## C. Baris perintah (JDK 17 + Android SDK terpasang)
    gradle :app:assembleDebug        # Gradle 8.9

APK debug sudah ditandatangani kunci debug dan bisa langsung dipasang.
Untuk rilis resmi, buat keystore sendiri lewat Build ▸ Generate Signed Bundle/APK.

Minimum Android 10 (API 29). Cara pakai aplikasi: lihat README di paket utama.
