# Archivo

Scanner dokumen offline untuk Android. Ambil foto halaman, deteksi tepi otomatis, potong & perbaiki hasil scan, ekstrak teks (OCR), kelola folder/tag, impor & anotasi PDF, serta scan QR/barcode — semuanya di perangkat, tanpa akun dan tanpa cloud.

**Package:** `com.yondex.archivo` · **Versi:** 1.0 · **minSdk:** 28 · **targetSdk:** 36

## Fitur

### Scan & dokumen
- **Scan kamera** — preview CameraX, deteksi tepi live (OpenCV), auto-capture saat dokumen stabil, senter, multi-halaman
- **Import galeri** — pilih beberapa gambar lewat Photo Picker
- **Crop perspektif** — sesuaikan sudut dokumen secara manual atau deteksi ulang otomatis
- **Filter** — Original, Scan warna, Grayscale, Hitam-putih
- **OCR** — pengenalan teks Latin (ML Kit), otomatis setelah simpan atau dijalankan ulang; teks bisa diedit dan dibagikan
- **Organisasi** — folder bersarang, tag berwarna, tampilan grid/list, urutkan, multi-select
- **Pencarian** — full-text search (FTS) di judul + hasil OCR, dengan filter folder/tag
- **Export** — PDF (A4 / Letter / sesuai gambar), JPG, PNG, atau TXT; bagikan atau simpan via SAF / galeri

### PDF & library
- **Impor PDF** — simpan ke library lokal, filter beranda (Semua / Scans / PDF)
- **Viewer PDF** — baca offline dengan toolbar anotasi
- **Rename file** — ganti judul tampilan dari Home atau reader
- **Highlight & comment** — sorot area halaman atau tempel komentar (tersimpan di Room)
- **Undo / Redo** — urungkan dan ulangi aksi anotasi (hingga 40 langkah)
- **Merge** — gabungkan beberapa dokumen scan dan/atau PDF jadi satu dokumen baru
- **Split** — ekstrak halaman terpilih dari dokumen atau PDF menjadi dokumen baru

### Lainnya
- **QR / barcode scanner** — scan via kamera (ML Kit), salin, bagikan, atau buka tautan
- **Bottom navigation** — Home, QR, Scan, Settings
- **Pengaturan** — bahasa (sistem/English/Indonesia), tema (sistem/terang/gelap), warna dinamis Material You, filter bawaan, OCR otomatis, ukuran & kualitas PDF
- **UI modern** — Material 3 flat, warna brand hijau, corner squircle, icon drawable kustom

## Tech stack

| Area | Teknologi |
|------|-----------|
| UI | Jetpack Compose, Material 3 |
| Arsitektur | MVVM, Hilt, Navigation Compose (type-safe routes) |
| Data | Room (+ FTS), DataStore Preferences |
| Kamera | CameraX |
| Vision | OpenCV 4 (tepi & processing), ML Kit Text Recognition, ML Kit Barcode |
| Background | WorkManager (OCR) |
| Gambar | Coil |

## Struktur proyek

Modul tunggal `:app`:

```
com.yondex.archivo
├── data/          Room, DataStore, repository, penyimpanan file
├── domain/        Model domain (dokumen, halaman, library, anotasi PDF, enum)
├── scanner/       Kamera, EdgeDetector, ImageProcessor, sesi scan
├── ocr/           TextRecognizer, OcrWorker, OcrScheduler
├── pdf/           ExportManager, JpegPdfWriter, PdfRasterizer
├── di/            Modul Hilt
└── ui/
    ├── home/      Beranda, filter, multi-select, import
    ├── scanner/   Kamera scan dokumen
    ├── review/    Review halaman sebelum simpan
    ├── crop/      Crop & filter
    ├── document/  Detail dokumen & export
    ├── page/      Viewer halaman + OCR
    ├── reader/    Viewer PDF + highlight/comment/undo-redo
    ├── merge/     Gabung dokumen & PDF
    ├── split/     Pisah halaman
    ├── qr/        QR / barcode scanner
    ├── search/    Pencarian FTS
    ├── tags/      Kelola tag
    ├── settings/  Preferensi
    ├── navigation/
    ├── components/
    └── theme/
```

Data bersifat **offline-first**: dokumen, PDF library, dan anotasi disimpan di storage privat aplikasi + Room; tidak ada backend.

## Persyaratan

- Android Studio (disarankan versi terbaru dengan dukungan AGP 9)
- JDK 17
- Perangkat/emulator **API 28+**
- Kamera untuk scan dokumen / QR (opsional — import galeri & PDF tetap bisa tanpa kamera)

## Build & run

```bash
# Windows
gradlew.bat :app:assembleDebug

# macOS / Linux
./gradlew :app:assembleDebug
```

Atau buka proyek di Android Studio lalu Run `app`.

OpenCV diambil dari Maven (`org.opencv:opencv`) dan diinisialisasi di `ArchivoApp` — tidak perlu checkout SDK OpenCV terpisah.

## Izin

| Izin | Keterangan |
|------|------------|
| `CAMERA` | Scan dokumen & QR (diminta saat membuka scanner) |

Import galeri memakai Photo Picker sistem (tanpa izin storage). Export memakai SAF / MediaStore / `FileProvider`.

## Alur singkat

1. **Scan / import** → halaman masuk ke sesi sementara, atau PDF masuk ke library  
2. **Review** → urutkan, putar, crop, filter  
3. **Simpan** → dokumen di Room + file gambar di storage lokal  
4. **OCR** (opsional/otomatis) → indeks FTS untuk pencarian  
5. **PDF** → baca, rename, highlight/comment, merge/split  
6. **Export / bagikan** → PDF, gambar, atau teks

## Lisensi

Belum ditentukan.
