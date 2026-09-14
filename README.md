# Dental Lab Management

A complete, **offline-first dental laboratory management system for Android**. Track
work orders from case intake to delivery, manage clinics, doctors and patients, handle
clinic-specific pricing, record account-level payments, generate monthly bills and
statements, and print patient warranty cards — all without any server, account, or
internet connection.

> Designed for small-to-medium dental laboratories that need a simple, private,
> single-device replacement for paper registers and spreadsheets.

## Project Status

**Active development — initial release (v1.0.0).** The feature set described in this
README is implemented and unit-tested, and the app is usable for daily lab work, but it
has not yet been hardened for production deployment (see the
[roadmap](docs/ROADMAP.md) — file-based backup/restore and multi-device sync are still
pending). Data is stored locally on one device; there is currently no cloud sync beyond
the optional Google Sheets mirror.

| | |
| --- | --- |
| Platform | Android (min SDK 24 / Android 7.0, target SDK 36) |
| Current version | 1.0.0 |
| Language | Kotlin |
| Data | On-device SQLite (Room) — offline-first |
| Accounts needed | None |

## Key Features

- **Work order management** — four-section entry form with automatic job numbering
  (`NDL-YYYY-NNNN`), clinic/patient/work-type linking, an interactive dental odontogram
  for tooth selection, shade/material presets, turnaround tracking and a full status
  workflow (Received → Pending → In Progress → Ready → Completed → Delivered /
  Cancelled).
- **Clinics & doctors** — clinic directory with per-clinic revenue and outstanding
  balances, a detailed clinic page with work history, and referential-integrity guards
  that protect historical data from accidental deletion.
- **Patients** — patient registry linked to clinics, with per-patient case counts and
  lifetime value.
- **Work types & pricing** — restoration catalog with unit-based and fixed-price
  pricing models, plus **per-clinic negotiated rates** that apply automatically to new
  orders while past orders keep their historical rates.
- **Payments & billing** — clinic-account-level payments (combined monthly settlement,
  never per-case attribution), outstanding balance per clinic, and full payment history.
- **Bills & statements** — monthly invoices and custom-period statements with
  previous-balance carry-forward, exportable as **PDF (printable), Excel and CSV**.
- **Reports & analytics** — financial overview (billed / collected / due, recovery
  rate), per-clinic and per-work-type breakdowns.
- **Warranty cards** — a dedicated Warranty Cards screen (sidebar) with search and
  clinic/year/Active-Expired filters: create cards from an existing work order or by
  manual entry, preview both sides, and export an **exactly-two-page CR80/ID-1
  (85.60 × 53.98 mm) PDF** (front + back) in a green-and-white design with a
  four-quadrant tooth-number diagram (FDI 1–8 per quadrant, only the teeth worked
  on), ready for front/back printing on card stock at Actual Size.
- **Import / Export** — XLSX ledgers (work orders, clinic directory, payments), CSV
  exports, CSV import for clinics and work orders, and a per-clinic work-history Excel
  export.
- **Settings & backup** — lab profile used across documents, optional manual
  **Google Sheets sync** of business data, JSON summary snapshot, demo-data reset.
- **100% offline-first** — every feature works in airplane mode; data never leaves the
  device unless you explicitly export or enable the optional sync.

Detailed per-feature documentation (workflows, fields, connections, limitations):
[docs/FEATURES.md](docs/FEATURES.md).

## Screenshots

> **Screenshots have not been added yet.**
> To add them later: capture screenshots or screen recordings of the app (Dashboard,
> New Work Entry with the odontogram, Work Orders, a Monthly Bill, the Warranty Card
> front/back preview), place them under `docs/screenshots/`, and reference them here,
> for example: `![Dashboard](docs/screenshots/dashboard.png)`.
> **Remember to blur or replace any real patient or clinic data before committing
> screenshots.**

## Technology Stack

| Layer | Technology |
| --- | --- |
| Language | Kotlin 2.2 |
| UI | Jetpack Compose + Material 3 (single-activity app) |
| Database | Room 2.7 over SQLite, with explicit migrations (never destructive) |
| Architecture | MVVM — Compose UI → `DentalLabViewModel` → `DentalLabRepository` → DAOs |
| Concurrency | Kotlin coroutines + Flow |
| PDF | Android framework `PdfDocument` (no third-party PDF library) |
| XLSX / CSV | Custom dependency-free writers |
| Networking | Retrofit + OkHttp + Moshi — used **only** by the optional Google Sheets sync |
| Build | Gradle (Kotlin DSL) + version catalog, AGP 9.1, KSP |
| Testing | JUnit 4, Robolectric, Roborazzi, coroutines-test |

## Architecture Overview

```
┌────────────────────────── UI — Jetpack Compose ──────────────────────────┐
│  ui/screens/*   ui/components/* (odontogram, work table, badges, cards) │
└────────────────────────────────┬─────────────────────────────────────────┘
                                 │ state / events
┌────────────────────────────────▼─────────────────────────────────────────┐
│                DentalLabViewModel (single AndroidViewModel)             │
└────────────────────────────────┬─────────────────────────────────────────┘
                                 │ suspend calls
┌────────────────────────────────▼─────────────────────────────────────────┐
│       DentalLabRepository — one facade for ALL data access              │
│  integrity guards · money math (MoneyUtils) · warranty cards · sync    │
└────────────────────────────────┬─────────────────────────────────────────┘
                                 │ Room DAOs
┌────────────────────────────────▼─────────────────────────────────────────┐
│        AppDatabase — SQLite on device (clinics, patients, work          │
│        types, clinic rates, work orders, payments, warranty cards,      │
│        lab settings, sync queue)                                        │
└──────────────────────────────────────────────────────────────────────────┘
        ▲                                    ▲
   export/* (PDF / XLSX / CSV generators     data/sync/* (optional manual
   + share / print / save-to-Downloads)      Google Sheets mirror)
```

Key design principles: offline-first (the device database is the only source of truth),
snapshotting (work orders freeze clinic/patient/work-type names and rates so history is
never rewritten), and clinic-account billing (payments belong to a clinic's account,
never to a single case).

More detail: [docs/DEVELOPMENT.md](docs/DEVELOPMENT.md) ·
[docs/DATABASE.md](docs/DATABASE.md).

## Installation / Setup

To use the app you need an Android device (7.0+) and one of:

- a prebuilt APK (when published under [Releases](https://github.com/njmuheeb/Dental_lab_Management_App/releases)),
  or
- an APK you build yourself (below).

Install the APK on the device (enable *Install unknown apps* for your browser/file
manager if Android asks). On first launch the app loads a fictional demo dataset so you
can explore safely — remove it with **Settings & Backup → Delete ALL Data** or **Reset
Demo Data**. No sign-up, permissions dialog, or internet connection is required for
normal use.

## How to Build the APK

Prerequisites: current [Android Studio](https://developer.android.com/studio) with its
bundled JDK (or a local Android SDK with platform 36 and a recent JDK — this project
was verified with JDK 25).

```bash
git clone https://github.com/njmuheeb/Dental_lab_Management_App.git
cd Dental_lab_Management_App

# Debug APK → app/build/outputs/apk/debug/app-debug.apk
./gradlew :app:assembleDebug
```

On Windows, use `gradlew.bat` instead of `./gradlew`.

Release builds expect signing credentials via the `KEYSTORE_PATH`, `STORE_PASSWORD` and
`KEY_PASSWORD` environment variables (never committed). Full details, including the
Windows Robolectric note and testing commands:
[docs/DEVELOPMENT.md](docs/DEVELOPMENT.md).

## How to Run the Project Locally

1. Open the cloned folder in Android Studio (**File → Open**).
2. Wait for the Gradle sync to finish (dependencies and SDK 36 download automatically
   on first sync).
3. Select an emulator or connected device (Android 7.0+) and press **Run ▶**.

Or from the command line: `./gradlew :app:installDebug` installs the debug build on a
connected device.

No API keys, `google-services.json`, or other configuration are needed — the project
builds and runs as-is.

## Basic Usage

1. **Set up your lab profile** — Settings & Backup: lab name, phone, address (used on
   bills and warranty cards).
2. **Add your clinics** — Clinics / Doctors → add each clinic and its doctor.
3. **Register patients** — Patients (or inline while creating an order).
4. **Create work orders** — **+ New**: pick clinic + patient, work type (rate
   auto-fills from the clinic's pricing), select teeth on the odontogram, shade,
   material, turnaround → Save.
5. **Track progress** — Work Orders → tap an order → Change Status.
6. **Record payments** — Payments & Billing → Record Clinic Payment (combined amount
   per clinic).
7. **Bill monthly** — open the clinic → Generate Bill → export PDF / Excel / CSV.
8. **Issue warranty cards** — from an order's details → Warranty Card → Print / Export
   PDF (two card-sized pages: front + back).

The full walkthrough with numbered steps: [docs/USER_GUIDE.md](docs/USER_GUIDE.md).

## Data Storage & Backup

- **Where data lives:** a private SQLite database on the device
  (`dental_lab_management.db`). Nothing is sent anywhere by default.
- **What is stored:** clinics, patients, work types, per-clinic rates, work orders,
  payments, warranty cards and lab settings. See
  [docs/DATABASE.md](docs/DATABASE.md) for the full schema and relationships.
- **Backups you can create today:**
  - XLSX / CSV ledger exports (Import / Export screen) — save to Downloads or share;
  - PDF / Excel bills and statements per clinic and period;
  - a JSON summary snapshot (Settings → Generate Backup, copied to the clipboard);
  - the optional Google Sheets mirror (manual sync of business data to a Sheet you own —
    see [docs/GOOGLE_SHEETS_SYNC_SETUP.md](docs/GOOGLE_SHEETS_SYNC_SETUP.md)).
- **Restore:** an in-app restore-from-file is not implemented yet (see the
  [roadmap](docs/ROADMAP.md)); treat the exports above as your archive.
- **Privacy:** the repository contains no real patient or clinic data; the demo dataset
  is fictional and deterministic.

## Project Structure

```
app/src/main/java/com/example/
├── MainActivity.kt                  # single activity, Compose host
├── data/
│   ├── dao/                         # Room DAOs
│   ├── database/                    # AppDatabase + migrations, demo data generator
│   ├── model/                       # Room entities, statement/report math
│   ├── repository/                  # DentalLabRepository — single data facade
│   ├── sync/                        # optional Google Sheets sync + delete outbox
│   └── util/                        # MoneyUtils, ToothFormat
├── export/                          # PDF / XLSX / CSV generators + FileExporter
└── ui/
    ├── DentalLabApp.kt              # scaffold, drawer/bottom-bar navigation
    ├── DentalLabViewModel.kt
    ├── components/                  # odontogram, work-order table, badges, stat cards
    ├── screens/                     # one file per feature screen
    └── theme/

docs/                                # user guide, features, database, development, …
app/src/test/java/com/example/       # Robolectric / JUnit unit tests
```

## Roadmap

Highlights (all **planned**, not yet implemented):

- File-based backup & restore
- PDF customization (logo, colors, terms templates)
- Expanded reporting (date ranges, per-doctor breakdowns)
- Warranty card browser list
- Multi-device synchronization
- CI via GitHub Actions

Full list with status markers: [docs/ROADMAP.md](docs/ROADMAP.md).

## Contributing

Bug reports, feature requests and pull requests are welcome. Please use the issue
templates ([bug report](.github/ISSUE_TEMPLATE/bug_report.md) /
[feature request](.github/ISSUE_TEMPLATE/feature_request.md)), read
[CONTRIBUTING.md](CONTRIBUTING.md) for the workflow and expectations, and **never post
real patient or clinic data**.

## License

Released under the [MIT License](LICENSE).

## Disclaimer

This software is provided as-is, without warranty of any kind. It is a management tool
for dental laboratory workflows — it is **not** a medical device, not certified for
clinical use, and does not replace professional or regulatory record-keeping
requirements. You are responsible for your own data: maintain regular exports of
anything you cannot afford to lose, and verify financial documents before relying on
them. The bundled demo dataset is fictional; any resemblance to real persons or clinics
is coincidental.
