# Archivo

Scanner dokumen offline untuk Android. Ambil foto halaman, deteksi tepi otomatis, potong & perbaiki hasil scan, ekstrak teks (OCR), lalu simpan dan kelola dalam folder dengan tag — semuanya di perangkat, tanpa akun dan tanpa cloud.

**Package:** `com.yondex.archivo` · **Versi:** 1.0 · **minSdk:** 28

## Fitur

- **Scan kamera** — preview CameraX, deteksi tepi live (OpenCV), auto-capture saat dokumen stabil, senter, multi-halaman
- **Import galeri** — pilih beberapa gambar lewat Photo Picker
- **Crop perspektif** — sesuaikan sudut dokumen secara manual atau deteksi ulang otomatis
- **Filter** — Original, Scan warna, Grayscale, Hitam-putih
- **OCR** — pengenalan teks Latin (ML Kit), otomatis setelah simpan atau dijalankan ulang per dokumen/halaman; teks bisa diedit dan dibagikan
- **Organisasi** — folder bersarang, tag berwarna, tampilan grid/list, urutkan, multi-select
- **Pencarian** — full-text search (FTS) di judul + hasil OCR, dengan filter folder/tag
- **Export** — PDF (A4 / Letter / sesuai gambar), JPG, PNG, atau TXT; bagikan atau simpan via SAF / galeri
- **Pengaturan** — tema (sistem/terang/gelap), warna dinamis Material You, filter bawaan, OCR otomatis, kualitas PDF

## Tech stack

| Area | Teknologi |
|------|-----------|
| UI | Jetpack Compose, Material 3 |
| Arsitektur | MVVM, Hilt, Navigation Compose |
| Data | Room (+ FTS), DataStore Preferences |
| Kamera | CameraX |
| Vision | OpenCV 4 (deteksi tepi & processing), ML Kit Text Recognition |
| Background | WorkManager (OCR) |
| Gambar | Coil |

## Struktur proyek

Modul tunggal `:app`:

```
com.yondex.archivo
├── data/          Room, DataStore, repository, penyimpanan file
├── domain/        Model domain (dokumen, halaman, Quad, enum)
├── scanner/       Kamera, EdgeDetector, ImageProcessor, sesi scan
├── ocr/           TextRecognizer, OcrWorker, OcrScheduler
├── pdf/           ExportManager, JpegPdfWriter
├── di/            Modul Hilt
└── ui/            Home, Scanner, Review, Crop, Document, Page, Search, Tags, Settings
```

Data bersifat **offline-first**: dokumen disimpan di storage privat aplikasi; tidak ada backend.

## Persyaratan

- Android Studio (disarankan versi terbaru dengan dukungan AGP 9)
- JDK 17
- Perangkat/emulator **API 28+**
- Kamera untuk scan (opsional — import galeri tetap bisa tanpa kamera)

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
| `CAMERA` | Scan dokumen (diminta saat membuka scanner) |

Import galeri memakai Photo Picker sistem (tanpa izin storage). Export memakai SAF / MediaStore / `FileProvider`.

## Alur singkat

1. **Scan / import** → halaman masuk ke sesi sementara  
2. **Review** → urutkan, putar, crop, filter  
3. **Simpan** → dokumen di Room + file gambar di storage lokal  
4. **OCR** (opsional/otomatis) → indeks FTS untuk pencarian  
5. **Export / bagikan** → PDF, gambar, atau teks

## Lisensi

Belum ditentukan.
