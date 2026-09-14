# Changelog

All notable changes to **Dental Lab Management** are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and this
project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added

- **Warranty Cards screen** (sidebar entry): searchable, filterable list of all issued
  cards (search by patient, clinic, card or work order number; filters by clinic,
  delivery year and Active/Expired status) with view (front/back preview dialog),
  edit, generate PDF, print and delete actions — plus a create dialog that starts a
  card from an existing work order (prefilled) or as manual entry optionally linked
  to an existing clinic and patient.
- Warranty card editor: work order number, care recommendations (printed on the card
  back) and internal notes fields.
- Configurable **default warranty period** in Settings → Laboratory Identity, used for
  new cards (per-card override unchanged).
- Professional card design: brand header with tagline and tooth glyph, work-type
  driven title ("{WORK TYPE} WARRANTY CARD"), warranty duration badge, patient panel,
  and a back side with warranty period strip, numbered terms & conditions, care
  recommendations and a policy disclaimer footer.
- "Print at Actual Size (100%)" guidance in the warranty card UI and export messages.

### Fixed

- **Warranty card PDF generation** — the front-side detail grid previously rendered as
  six stacked rows, pushing the "DELIVERED ON" and "VALID UNTIL" fields off the card and
  overlapping the footer. The card is now laid out as a proper two-column, three-row
  grid, and every text element is measured against the page bounds.
- Long patient names, addresses and tooth lists now wrap safely on the warranty card;
  unbreakable long words are hard-broken instead of being clipped at the card edge.
- The patient phone number is now always printed on the warranty card front side
  (previously it was hidden whenever the patient name needed two lines).
- The patient address (when provided) is now printed on the warranty card front side.
- Warranty terms on the card back now auto-shrink to fit instead of silently dropping
  lines when the text is long.
- The Android print adapter now reports the exact page count (2) for warranty card PDFs.

### Changed

- Warranty cards store a clinic name snapshot (`clinicName`, schema v6) and the database
  file was renamed to `dental_lab_management.db`; existing data is migrated in place.
- Warranty card PDF pages are explicitly sized CR80/ID-1 (85.60 × 53.98 mm) on both pages.
- Lab branding across the app, documents and exports now uses the generic name
  "Dental Lab Management".

## [1.0.0] - Initial Development Release - 2026-09-13

First feature-complete development build of the offline-first dental laboratory
management app. Not yet production-hardened — see the [roadmap](docs/ROADMAP.md).

### Added

- **Dashboard** — revenue / collected / outstanding KPIs with period filters (all time,
  today, this week, this month), production pipeline counters, urgent-delivery alerts,
  clinic & patient counters and the five most recent work orders.
- **Work orders** — searchable, filterable and sortable ledger with a four-section entry
  form (clinic & patient, work specification with unit-based / fixed-price pricing,
  interactive dental odontogram for tooth selection, shade / material / turnaround /
  instructions), automatic job numbering (`NDL-YYYY-NNNN`), a full details dialog,
  status workflow (Received → … → Delivered / Cancelled) and deletion.
- **Clinics & doctors** — clinic directory with per-clinic revenue and outstanding
  balance, clinic profile management, referential-integrity guards on delete, and a
  clinic detail screen with account statistics, work history, payment recording and
  bill / statement entry points.
- **Patients** — patient registry linked to a clinic with case counts and lifetime
  value per patient.
- **Work types** — restoration catalog with categories, unit-based or fixed-price
  pricing models and default rates.
- **Clinic-specific pricing** — per-clinic rate overrides per work type; future orders
  pick the negotiated rate automatically while past orders keep historical rates.
- **Payments & billing** — clinic-account-level payments (never per case), monthly
  billing with previous-balance carry-forward, and full clinic statements for any date
  range; outstanding balances are always computed per clinic account.
- **Reports & analytics** — a reports screen built from the same statement engine.
- **Import / Export** — XLSX work-order ledger, clinic directory and payments ledger
  exports; PDF / XLSX / CSV outputs for monthly bills and statements.
- **Warranty cards** — per-work-order patient warranty cards stored in the database,
  prefilled from the order/patient/clinic/lab settings, editable, printable and
  exportable as a two-page CR80 (Aadhaar-sized, 85.6 × 54 mm) PDF (front / back).
- **Settings & backup** — lab profile, theme mode, currency symbol, default turnaround
  and payment method, demo-data reset, JSON backup snapshot, and optional manual
  Google Sheets sync of business data.
- **Infrastructure** — Room database (v5 at release) with non-destructive migrations,
  offline-first single-device architecture, deterministic fictional demo dataset,
  Robolectric / JUnit test suite.
