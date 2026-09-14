# Database Documentation

This document describes the actual database implementation of **Dental Lab Management**.
It is aimed at developers who want to understand how data is stored, how the modules
relate to each other, and how migrations work.

> **Privacy note:** this repository contains **no real data**. The database is created on
> the user's device at first launch. The bundled demo dataset is deterministic and
> entirely fictional (see `DemoDataGenerator.kt`).

## Technology

| Concern | Implementation |
| --- | --- |
| Database engine | SQLite (via Android) |
| Access layer | Room 2.7.0 (`androidx.room`) with KSP code generation |
| Database file | `dental_lab_management.db` (app-private internal storage) |
| Schema version | **8** |
| Threading | DAOs are suspend functions / `Flow`, called off the main thread through the repository |
| Architecture | Single `AppDatabase`, one DAO per entity, unified access via `DentalLabRepository` |

The application is **offline-first**: the on-device Room database is the only source of
truth. All features (dashboard, work orders, billing, reports, exports) work with no
network connection. The optional Google Sheets sync (see
[Google Sheets Sync Setup](GOOGLE_SHEETS_SYNC_SETUP.md)) only *pushes* data out; it never
modifies the local database.

## Tables

All entity classes live in `app/src/main/java/com/example/data/model/Entities.kt`.
DAOs are declared in `app/src/main/java/com/example/data/dao/DentalLabDaos.kt`.

### `clinics`

| Column | Type | Notes |
| --- | --- | --- |
| `id` | INTEGER PK | auto-generated |
| `clinicCode` | TEXT | unique (e.g. `CLN-101`) |
| `name`, `dentistName`, `phone`, `whatsapp`, `email`, `address`, `city`, `notes` | TEXT | clinic profile |
| `isActive` | INTEGER | soft-disable flag |
| `createdAt`, `updatedAt` | INTEGER | epoch millis |
| `syncId` | TEXT | unique UUID (sync deduplication) |
| `pendingSync` | INTEGER | change-tracking flag for the optional sync |

### `patients`

| Column | Type | Notes |
| --- | --- | --- |
| `id` | INTEGER PK | auto-generated |
| `patientCode` | TEXT | indexed (not unique) |
| `name`, `phone`, `gender`, `notes` | TEXT | patient profile |
| `age` | INTEGER | nullable |
| `clinicId` | INTEGER | **FK → clinics.id** (patient belongs to a clinic) |
| `dentistName` | TEXT | optional treating doctor |
| `createdAt`, `updatedAt`, `syncId`, `pendingSync` | — | audit + sync |

### `work_types`

| Column | Type | Notes |
| --- | --- | --- |
| `id` | INTEGER PK | auto-generated |
| `name`, `description` | TEXT | e.g. "Zirconia Crown (Monolithic)" |
| `category` | TEXT | Crown & Bridge, Dentures, Implants, Orthodontics, Repairs, Cosmetic, Custom |
| `pricingModel` | TEXT | `UNIT_BASED` (price × units) or `FIXED_PRICE` (flat per case) |
| `defaultRate` | REAL | fallback rate when no clinic-specific rate exists |
| `isActive` | INTEGER | soft-disable flag |
| `createdAt`, `updatedAt`, `syncId`, `pendingSync` | — | audit + sync |

### `clinic_rates` (clinic-specific pricing)

| Column | Type | Notes |
| --- | --- | --- |
| `id` | INTEGER PK | auto-generated |
| `clinicId` | INTEGER | **FK → clinics.id** |
| `workTypeId` | INTEGER | **FK → work_types.id** |
| `pricingModel` | TEXT | can override the work type's model |
| `customRate` | REAL | the negotiated rate |
| `updatedAt`, `syncId`, `pendingSync` | — | audit + sync |

There is a **unique index on (`clinicId`, `workTypeId`)** — one rate per clinic per work
type. Effective rate resolution: `clinic_rates` value wins, otherwise the work type's
`defaultRate` (see `DentalLabRepository.getEffectiveRate`).

### `work_orders` (the core table)

| Column | Type | Notes |
| --- | --- | --- |
| `id` | INTEGER PK | auto-generated |
| `jobNumber` | TEXT | **unique**, format `NDL-YYYY-NNNN`, generated sequentially |
| `entryDate`, `expectedDeliveryDate` | INTEGER | set at creation |
| `actualDeliveryDate` | INTEGER | nullable, set when delivered |
| `clinicId`, `clinicName` | INTEGER / TEXT | **FK → clinics.id** + name snapshot |
| `dentistName` | TEXT | doctor for this case |
| `patientId`, `patientName` | INTEGER / TEXT | **FK → patients.id** + name snapshot |
| `caseNumber` | TEXT | optional clinic-side case number |
| `workTypeId`, `workTypeName` | INTEGER / TEXT | **FK → work_types.id** + name snapshot |
| `pricingModel` | TEXT | snapshot (`UNIT_BASED` / `FIXED_PRICE`) |
| `selectedTeeth` | TEXT | comma-separated **FDI** numbers, e.g. `11,12,21` |
| `shade`, `material` | TEXT | e.g. `A2`, `Multilayer Zirconia` |
| `units` | INTEGER | tooth/bridge count (unit-based pricing) |
| `rate` | REAL | historical snapshot of the effective rate |
| `totalAmount` | REAL | `units × rate` or flat rate (recomputed on save via `MoneyUtils`) |
| `status` | TEXT | `Received`, `Pending`, `In Progress`, `Ready`, `Completed`, `Delivered`, `Cancelled` |
| `notes`, `specialInstructions` | TEXT | free text |
| `createdAt`, `updatedAt`, `syncId`, `pendingSync` | — | audit + sync |

**Snapshots by design:** clinic name, patient name, work type name, pricing model and
rate are copied into the row at creation so that later edits to the master records never
silently rewrite financial history.

### `payments` (clinic-account level)

| Column | Type | Notes |
| --- | --- | --- |
| `id` | INTEGER PK | auto-generated |
| `clinicId` | INTEGER | **FK → clinics.id** |
| `amount` | REAL | rounded to 2 decimals on insert (`MoneyUtils.round2`) |
| `paymentDate` | INTEGER | epoch millis |
| `paymentMethod` | TEXT | `Cash`, `UPI`, `Bank Transfer`, `Card`, `Cheque` (as offered in the UI) |
| `referenceNumber`, `notes` | TEXT | free text |
| `createdAt`, `updatedAt`, `syncId`, `pendingSync` | — | audit + sync |

Payments are **never tied to an individual work order**. Clinics settle monthly at the
account level; outstanding balance per clinic is computed as:

```
outstanding(clinic) = sum(work_orders.totalAmount for non-cancelled orders)
                    - sum(payments.amount)
```

This is the core billing model of the app — combined monthly payments are never
(incorrectly) distributed across individual cases.

### `warranty_cards`

| Column | Type | Notes |
| --- | --- | --- |
| `id` | INTEGER PK | auto-generated |
| `workOrderId` | INTEGER | **unique, nullable** — FK → work_orders.id (`NULL` = standalone card) |
| `workOrderNumber` | TEXT | snapshot of `work_orders.jobNumber` (`''` for standalone cards) |
| `clinicId`, `clinicName` | INTEGER / TEXT | **FK → clinics.id** + name snapshot (0/'' when a manual card has no link) |
| `patientId`, `patientName` | INTEGER / TEXT | **FK → patients.id** + name snapshot |
| `patientAddress`, `patientPhone` | TEXT | printed on the card |
| `workType`, `material`, `shade` | TEXT | work details |
| `selectedTeeth` | TEXT | raw FDI list e.g. `12,13,21,47` — drives the four-quadrant diagram on the printed card |
| `toothNumbers` | TEXT | quadrant notation e.g. `UR: 1, 2 \| LL: 6` (legacy rows without `selectedTeeth` are parsed back from this at render time) |
| `consultantDoctor` | TEXT | e.g. `Dr. Sameer Khan` |
| `deliveryDate` | INTEGER | issue date of the card |
| `warrantyYears` | INTEGER | duration (1–20) |
| `warrantyExpiryDate` | INTEGER | recomputed on every save (`deliveryDate + years`) |
| `cardNumber` | TEXT | format `WC-YYYY-NNNN` |
| `terms` | TEXT | warranty terms text (editable, printed on the card back) |
| `careInstructions` | TEXT | care recommendations (editable, printed on the card back) |
| `notes` | TEXT | internal note — stored on record, deliberately **not printed** |
| `labName`, `labAddress`, `labPhone` | TEXT | lab identity printed on the card |
| `createdAt`, `updatedAt`, `syncId`, `pendingSync` | — | audit + sync |

The card PDF (see `export/WarrantyCardPdfExporter.kt`) is exactly two CR80/ID-1 card-sized
pages: page 1 front, page 2 back.

### `lab_settings` (single row, `id = 1`)

| Column | Type | Notes |
| --- | --- | --- |
| `labName`, `phone`, `whatsapp`, `email`, `address`, `city` | TEXT | lab identity used on bills, statements and warranty cards |
| `currencySymbol` | TEXT | default `₹` |
| `defaultTurnaroundDays` | INTEGER | default 5, applied to new work orders |
| `defaultWarrantyYears` | INTEGER | default 10, applied to new warranty cards (per-card override available) |
| `defaultPaymentMethod` | TEXT | default `UPI` |
| `themeMode` | TEXT | `System` / `Light` / `Dark` |
| `syncEnabled`, `syncUrl`, `syncSecret`, `lastSyncAt` | — | optional Google Sheets sync configuration (user-entered; never compiled in) |

### `sync_queue` (outbox for deletes)

| Column | Type | Notes |
| --- | --- | --- |
| `id` | INTEGER PK | |
| `entityType` | TEXT | `clinic`, `patient`, `work_type`, `clinic_rate`, `work_order`, `payment` |
| `entitySyncId` | TEXT | the `syncId` of the deleted row |
| `operation` | TEXT | `DELETE` |
| `createdAt` | INTEGER | |

A deleted row cannot carry a `pendingSync` flag, so deletes are recorded as tombstones
and pushed by the optional sync. Unique on (`entityType`, `entitySyncId`).

## Relationship diagram

```
                    ┌──────────────┐
                    │   clinics    │
                    └──────┬───────┘
             ┌─────────────┼──────────────┬───────────────┐
             │ 1:N         │ 1:N          │ 1:N           │ 1:N
             ▼             ▼              ▼               ▼
        ┌──────────┐  ┌────────────┐  ┌───────────┐  ┌───────────┐
        │ patients │  │ work_orders│  │  payments │  │clinic_rates│
        └────┬─────┘  └─────┬──────┘  └───────────┘  └─────┬─────┘
             │ 1:N          │ 1:1                          │ N:1
             └─────────────►│ warranty_cards               │
                   (via     └──────────────┬───────────────┘
                    patientId)             │ N:1
                                           ▼
                                    ┌────────────┐
                                    │ work_types │
                                    └────────────┘
```

- `work_orders.clinicId → clinics.id`
- `work_orders.patientId → patients.id` (`patients.clinicId → clinics.id`)
- `work_orders.workTypeId → work_types.id`
- `clinic_rates.(clinicId, workTypeId)` → one negotiated rate per clinic per work type
- `payments.clinicId → clinics.id` (account level, **no work-order link by design**)
- `warranty_cards.workOrderId → work_orders.id` (unique, nullable) plus direct
  `clinicId` / `patientId` links

## Referential integrity

There are no SQLite `FOREIGN KEY` constraints; integrity is guarded in
`DentalLabRepository` instead (business rules that Room cannot express):

- A clinic **cannot be deleted** while it still has work orders or patients.
- A patient **cannot be deleted** while they still have work orders.
- A work type **cannot be deleted** while it is used by work orders or clinic rates.
- Deleting a work order does **not** touch payments (they are account level).
- Deleting a clinic/patient/work type records a tombstone in `sync_queue`.

## Migrations

Room migrations are registered in `AppDatabase.kt` via `addMigrations(...)` — the app
never falls back to destructive migration, so user data is preserved across upgrades.

| Migration | Change |
| --- | --- |
| 1 → 2 | Sync-ready columns (`syncId` unique, `pendingSync`, missing `updatedAt`), sync settings columns on `lab_settings`, new `sync_queue` table |
| 2 → 3 | Switch to the clinic-account payment model: `payments` drops per-case columns, `work_orders` drops the per-case paid-amount cache (table rebuilds, data preserved) |
| 3 → 4 | New `warranty_cards` table |
| 4 → 5 | `warranty_cards.workOrderId` becomes nullable (standalone cards allowed; table rebuild) |
| 5 → 6 | `warranty_cards.clinicName` snapshot column added and backfilled from `clinics`; stored rows carrying the retired default lab branding are replaced with the generic name |
| 6 → 7 | `warranty_cards` gains `workOrderNumber` (backfilled from work orders), `careInstructions` (backfilled with the default care recommendations) and `notes`; `lab_settings` gains `defaultWarrantyYears` |
| 7 → 8 | `warranty_cards` gains `selectedTeeth` (raw FDI list for the quadrant diagram); older rows fall back to parsing `toothNumbers` at render time, so no SQL backfill is needed |

The database file was renamed from a previous internal name to
`dental_lab_management.db`; on first launch after the rename the app copies the old file
(plus its WAL/SHM sidecars) so existing local data survives.

## Money handling

- Amounts are stored as REAL and every write is rounded through `MoneyUtils.round2`.
- `WorkOrder.totalAmount` is always recomputed on insert/update from `units`, `rate` and
  `pricingModel` — never trusted from the UI.
- Display formatting goes through `MoneyUtils` (INR-style grouping with `₹`).

## Tooth notation

Teeth are stored as comma-separated **FDI** numbers on work orders (`selectedTeeth`,
e.g. `11,12,21`) and rendered for humans in quadrant notation via `ToothFormat`
(e.g. "Upper Right: 1, 2"). Warranty cards store the human-readable quadrant string.

## Backup behaviour

- **Exports (always available):** PDF / XLSX / CSV documents are generated from the
  database on demand and can be shared, printed, or saved to Downloads. XLSX exports are
  written as files; CSV exports are placed on the clipboard through a preview dialog.
- **CSV import:** the Import / Export screen can import clinics and work orders from
  pasted CSV text (template provided in-app). Work-order imports are basic: rows are
  attached to the first registered clinic with limited field mapping.
- **JSON backup snapshot:** Settings → *Generate Backup* produces a JSON **summary**
  snapshot (timestamp, record counts, totals and a per-work-order digest) that is copied
  to the clipboard. It is a quick audit/reference snapshot — **not** a full database
  export, and there is currently no in-app restore path (see the
  [roadmap](ROADMAP.md)).
- **Optional Google Sheets mirror:** pushes clinics, patients, work types, clinic rates,
  work orders and payments to the user's own Google Sheet. Lab settings (including the
  sync secret) and warranty cards are intentionally **not** synced. See
  [Google Sheets Sync Setup](GOOGLE_SHEETS_SYNC_SETUP.md).
