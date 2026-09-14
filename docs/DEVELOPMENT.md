# Development Guide

This document describes the actual technology, architecture, build system and developer
workflow of **Dental Lab Management**, verified against the repository.

## Technology stack

| Concern | Technology |
| --- | --- |
| Language | Kotlin **2.2.10** |
| UI | Jetpack Compose (BOM 2024.09.00) + Material 3 + Material Icons Extended |
| Navigation | Drawer-based destination switching managed in `ui/DentalLabApp.kt` with overlay screens |
| Database | Room **2.7.0** over SQLite (KSP code generation) |
| Architecture | MVVM with a single repository facade (`DentalLabRepository`) |
| Concurrency | Kotlin coroutines + Flow (`Dispatchers.IO` for all database work) |
| Network | Retrofit 2.12.0 + OkHttp 4.10.0 + Moshi 1.15.2 — used **only** by the optional Google Sheets sync |
| PDF | `android.graphics.pdf.PdfDocument` (framework API — no third-party PDF library) |
| XLSX / CSV | Dependency-free custom writers (`export/XlsxWriter.kt`, `export/CsvExporters.kt`) |
| Build | Gradle (Kotlin DSL), AGP **9.1.1**, version catalog `gradle/libs.versions.toml` |
| Min SDK | **24** (Android 7.0) |
| Target / Compile SDK | **36** |
| Tests | JUnit 4, Robolectric 4.16.1, Roborazzi 1.59.0, kotlinx-coroutines-test, Compose UI test |

Other declared dependencies (Firebase BOM with AppCheck/AI entries, Coil, CameraX,
Accompanist, Credential Manager) are mostly **commented out or unused placeholders** in
`app/build.gradle.kts`; the app builds and runs without any Firebase project or
`google-services.json` (the google-services plugin is configured with
`missingGoogleServicesStrategy = WARN` and `googleServices.missing.passthrough=true`).

## Architecture

```
┌───────────────────────────── UI (Compose) ─────────────────────────────┐
│  ui/screens/*   ui/components/*   ui/theme/*                          │
│  stateless screens driven by DentalLabViewModel state                 │
└──────────────────────────────┬─────────────────────────────────────────┘
                               │ state / events
┌──────────────────────────────▼─────────────────────────────────────────┐
│  ui/DentalLabViewModel  (AndroidViewModel)                             │
└──────────────────────────────┬─────────────────────────────────────────┘
                               │ suspend calls
┌──────────────────────────────▼─────────────────────────────────────────┐
│  data/repository/DentalLabRepository  — single data-access facade     │
│  • referential-integrity guards   • money math via MoneyUtils        │
│  • warranty card lifecycle        • sync outbox (tombstones)         │
└──────────────────────────────┬─────────────────────────────────────────┘
                               │ DAO interfaces (Room)
┌──────────────────────────────▼─────────────────────────────────────────┐
│  data/database/AppDatabase  (Room, SQLite on device)                  │
└─────────────────────────────────────────────────────────────────────────┘

export/*  — pure generators (PDF / XLSX / CSV bytes) + FileExporter
            (share sheet, Android print framework, save to Downloads)
data/sync/ — optional Google Sheets mirror (manual trigger, user-configured)
```

Key principles:

- **Offline-first**: the Room database on the device is the only source of truth. All
  screens work with airplane mode on.
- **Single facade**: every mutation goes through `DentalLabRepository`, so integrity
  rules (no deleting clinics with history, etc.), snapshotting and sync flags are applied
  consistently.
- **Snapshotting**: work orders copy clinic/patient/work-type names and the effective
  rate at creation time, so editing master records never rewrites financial history.
- **Export generators are pure**: `PdfExporter`, `WarrantyCardPdfExporter`,
  `XlsxExporters` and `CsvExporters` turn data into bytes and never touch the database.

## Source layout

```
app/src/main/java/com/example/
├── MainActivity.kt                  # single activity, Compose host
├── data/
│   ├── dao/DentalLabDaos.kt         # Room DAOs (one interface per entity)
│   ├── database/AppDatabase.kt      # Room database + migrations v1→v6
│   ├── database/DemoDataGenerator.kt# deterministic fictional demo dataset
│   ├── model/Entities.kt            # Room entities
│   ├── model/ReportModels.kt        # statement/report calculators
│   ├── repository/DentalLabRepository.kt
│   ├── sync/SheetsSyncService.kt    # optional Google Sheets push
│   ├── sync/SyncQueue.kt            # delete tombstones outbox
│   └── util/MoneyUtils.kt, ToothFormat.kt
├── export/
│   ├── PdfExporter.kt               # A4 statements / monthly bills
│   ├── WarrantyCardPdfExporter.kt   # 2-page CR80 warranty card PDF
│   ├── XlsxWriter.kt, XlsxExporters.kt
│   ├── CsvExporters.kt
│   └── FileExporter.kt              # share / print / save-to-Downloads
└── ui/
    ├── DentalLabApp.kt              # scaffold, drawer, navigation, overlays
    ├── DentalLabViewModel.kt
    ├── components/                  # WorkOrderTable, StatCard, badges, odontogram
    ├── screens/                     # one file per feature screen
    └── theme/                       # Material 3 theme, colors, typography

app/src/test/java/com/example/       # Robolectric / JUnit unit tests
```

## Getting started

### Prerequisites

- **Android Studio** current stable (required for AGP 9.1.1 / SDK 36 support)
- **A recent JDK to run Gradle** — this project was verified with JDK 25; the JDK
  bundled with current Android Studio also works (the app *bytecode* itself targets
  Java 11)
- Android SDK Platform 36 (Android Studio will offer to download it on first sync)
- A device or emulator running **Android 7.0 (API 24)** or newer

### Clone and run

```bash
git clone https://github.com/njmuheeb/Dental_lab_Management_App.git
cd Dental_lab_Management_App
```

Then either:

**Option A — Android Studio**

1. Open the project folder (`File → Open`).
2. Let Gradle sync finish (SDK 36 and dependencies are downloaded automatically).
3. Select a device/emulator and press **Run**.

**Option B — Command line**

```bash
# Debug build + install on a connected device/emulator
./gradlew :app:installDebug
```

No API keys or `google-services.json` are required to build or run the app.

## Build commands

```bash
./gradlew :app:assembleDebug        # debug APK  → app/build/outputs/apk/debug/
./gradlew :app:assembleRelease      # release APK (see signing below)
./gradlew build                     # everything: assemble + check
```

On Windows use `gradlew.bat` (e.g. `gradlew.bat :app:assembleDebug`).

### Release signing

The `release` build type expects signing credentials through environment variables
(never committed):

| Variable | Meaning |
| --- | --- |
| `KEYSTORE_PATH` | path to your keystore (defaults to `<repo>/my-upload-key.jks` if unset) |
| `STORE_PASSWORD` | keystore password |
| `KEY_PASSWORD` | key password (alias is `upload`) |

The debug build type uses a local debug keystore (`debug.keystore`, git-ignored, password
`android` — the Android default).

### Secrets handling

The [Secrets Gradle Plugin](https://github.com/google/secrets-gradle-plugin) reads
`.env` (git-ignored) with `.env.example` as the default template. No keys are required
for normal use; anything configured there is injected as a `BuildConfig` field at build
time.

## Testing

```bash
# All unit tests (Robolectric + plain JUnit)
./gradlew :app:testDebugUnitTest

# A single test class
./gradlew :app:testDebugUnitTest --tests "com.example.WarrantyCardTest"

# Instrumented tests (requires a connected device/emulator)
./gradlew :app:connectedDebugAndroidTest
```

- Unit tests live in `app/src/test/java/com/example/` and run on the JVM via
  **Robolectric** (`@Config(sdk = [36])`, NATIVE graphics mode where needed).
- Coverage includes repositories, the sync outbox, money/tooth formatting, XLSX/CSV
  writers, warranty card lifecycle + PDF layout invariants.
- Test reports: `app/build/reports/tests/testDebugUnitTest/index.html`

### Windows note (Robolectric native runtime)

Robolectric cannot load its native runtime when the project path contains spaces
(e.g. `C:\Users\John Doe\...`). The build works around this with an **offline
dependencies directory** (`C:/Android/robolectric-deps` by default, configurable via the
`ROBOLECTRIC_DEPS_DIR` environment variable) — see `testOptions` in
`app/build.gradle.kts`. If that directory does not exist, plain `RobolectricTestRunner`
tests may fail on machines whose home path contains a space.

## Useful Gradle properties

`gradle.properties` enables: Gradle/configuration cache, parallel builds, in-process
Kotlin compilation, and `googleServices.missing.passthrough=true` (build without a
Firebase config).

## Conventions

- Kotlin official code style; no detekt/spotless is configured — keep formatting
  consistent with the existing files.
- Database schema changes require a Room migration (see `docs/DATABASE.md`) and a
  version bump in `AppDatabase`.
- All user-visible currency math goes through `MoneyUtils`; tooth display goes through
  `ToothFormat`.
- Exported PDFs use base-14 fonts — keep document text ASCII-only.
