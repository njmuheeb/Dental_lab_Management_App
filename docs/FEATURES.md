# Features

This document describes every feature actually implemented in **Dental Lab Management**,
verified against the source code. Screenshots are not included yet (see the
[README](../README.md#screenshots)).

The app is a **single-activity Jetpack Compose application** with a navigation drawer,
a bottom navigation bar (Dashboard, Work Orders, **+ New**, Clinics, Payments) and
several full-screen overlays (clinic detail, monthly bill, clinic statement, warranty
card). All data lives in an on-device Room/SQLite database — the app is fully usable
offline. See [DATABASE.md](DATABASE.md) for the data model.

Money is displayed in Indian grouping format (e.g. ₹1,23,456) throughout; dates use
`dd MMM yyyy`.

---

## A. Dashboard

**What it does:** lab overview with financial KPIs, production pipeline counters,
urgent-delivery alerts and the latest activity.

**Workflow:**

1. A banner shows the lab name (from Settings).
2. Pick a period with the filter chips: **All Time / Today / This Week / This Month**.
3. Read the financial KPI cards:
   - **Total Revenue** — sum of non-cancelled work orders in the period (+ order count)
   - **Collected** — payments received in the period
   - **Outstanding** — revenue minus collected, floored at zero
4. **Production Pipeline** counters: Pending (includes "Received"), In Progress, Ready,
   Completed, Delivered.
5. **Urgent / Upcoming Deliveries** (shown when non-empty): orders due within 2 days or
   overdue, marked OVERDUE or "Due <date>"; tapping one opens it in Work Orders.
6. **Active Clinics** and **Registered Patients** counter cards (tap to jump to the
   respective lists).
7. **Recent Work Orders**: the 5 newest orders with a *View All* shortcut.

**Connections:** tapping an urgent or recent order opens the Work Orders screen with
that order's details dialog.

**Limitations:** KPIs are filtered by order *entry date*; the "Pending" pipeline counter
also counts "Received" orders.

---

## B. Work Orders

**What it does:** the production ledger — create, search, filter, sort, inspect, update
status and delete work orders. This is the core module of the app.

### Creating a work order (New Work Entry screen)

The form has four sections:

1. **Clinic & Patient Details**
   - *Dental Clinic*\* dropdown (active clinics) — with a quick **+** button to add a new
     clinic inline (name, dentist, phone, city).
   - *Dentist / Doctor* (auto-filled from the clinic, editable), *Case / Ref #*.
   - *Patient*\* dropdown (patients of the selected clinic) — with a quick add dialog
     (name, phone, age, gender); the new patient is linked to the selected clinic.
2. **Work Restoration & Pricing**
   - *Work Type / Service*\* dropdown.
   - Pricing model switch: **Unit-Based (Units × Rate)** or **Fixed-Price (flat)**.
   - *Units*\* (unit-based only) and *Rate (₹)\** — the rate is **auto-filled with the
     effective rate** for the chosen clinic + work type (clinic-specific rate if one is
     configured, otherwise the work type's default rate) and remains editable.
   - Live **TOTAL** preview.
3. **Tooth Number Selection (Odontogram)** — interactive tooth chart (see
   [Dental Odontogram](#dental-odontogram)). With unit-based pricing, the unit count
   auto-syncs to the number of selected teeth.
4. **Shade, Material & Production**
   - *Shade* combo (A1, A2, A3, A3.5, B1, B2, C1, Bleach BL1/BL2 or free text) and
     *Material* combo (Zirconia variants, E-Max, PFM, Co-Cr, acrylics, Valplast or free
     text).
   - Turnaround chips (3d / 5d / 7d / 10d) with the computed delivery date.
   - *Special Instructions / Technician Notes*.

The job number is generated automatically (`NDL-YYYY-NNNN`, sequential per year). New
orders start in status **Received** (with the default turnaround from Settings).
Validation walks clinic → patient → work type → units.

### The work order list

- Search by job number, clinic, patient, work type or teeth.
- Status filter chips: All, Received, Pending, In Progress, Ready, Completed, Delivered,
  Cancelled.
- Sort: Newest, Oldest, Total High, Delivery Due.
- Each card shows job number, dates, pricing-model + status badges, patient, clinic,
  work type, teeth/shade, and the total (with "units × rate" detail for unit-based
  pricing).

### Order details dialog

Clinical information (clinic, doctor, patient, case number), work specification (type,
pricing model, shade/material, units & rate, special instructions), a read-only
odontogram with the tooth list, and billing information with a note that clinics settle
monthly at the account level. From here you can **change the status** (any of the 7
statuses), open the **Warranty Card**, or delete the order.

**Connections:** work orders link a clinic + patient + work type; the warranty card is
created from an order; clinic detail shows the clinic's orders; every ledger, bill and
statement is built from work orders.

**Limitations:** status changes are free-form (no enforced sequence). Full order editing
is available from the clinic's work-history table (Clinic Detail screen), not from this
list — here you can change status and delete. Deleted orders are removed permanently
(payments are account-level and unaffected).

---

## C. Clinics & Doctors

**What it does:** directory of dental clinics with per-clinic financials and a full
clinic detail screen.

**Clinic list:** search by name, doctor, city or phone; *Active Only* filter switch.
Each card shows the doctor and city, contact, and three figures: **Orders**, **Total
Revenue** (non-cancelled) and **Outstanding Due** (revenue − payments). Add/edit dialog
fields: Clinic Name\*, Dentist / Doctor\*, Phone, City, Address, Lab Notes /
Preferences, Active Status switch.

**Clinic detail screen** (tap a clinic):

- Contact card (phone, email, address, notes, entry/patient counts).
- Account stats: Work Entries, Total Revenue, Payments Received, Outstanding.
- Actions: **Record Payment**, **Generate Bill**, **View Statement**, **Export Excel**
  (work history `.xlsx`), **+ New Work**.
- Work history table with month + status filters and search; tapping a row (or its edit
  button) opens the **full work-order editor** (patient, work type, pricing model,
  odontogram, shade, case #, material, units, rate, delivery days, status, notes — with
  the total recomputed live). Saving with status *Delivered* stamps the actual delivery
  date.
- Deleting a work order from the table is permanent (payments stay, they are
  account-level).

**Guards:** a clinic cannot be deleted while it has work orders or registered patients;
a patient cannot be deleted while they have work orders; a work type cannot be deleted
while used by orders or clinic rates. The confirmation dialogs warn about this.

**Limitations:** the work-order editor cannot move an order to a different clinic; the
"Pending" status is not offered in the editor's status chips.

---

## D. Patients

**What it does:** patient registry linked to clinics.

- Fields: Patient Full Name\*, Phone, Age, Gender (Male / Female / Other), Associated
  Clinic\* (every patient belongs to one clinic), Medical / Dental Notes.
- Search by name, patient code or phone.
- Each card shows the clinic, number of cases and the patient's total value
  (non-cancelled orders).
- Patients are created either here or inline while creating a work order; the patient
  code is generated (`PAT-NNN`).

**Connections:** patients feed the New Work Entry dropdown; deleting a patient is
blocked once they have work orders (their orders keep the name snapshot).

**Limitations:** no patient detail/drill-down screen yet.

---

## E. Work Types (restoration catalog)

**What it does:** catalog of services/restorations the lab offers.

- Fields: Restoration / Work Name\*, Category (Crown & Bridge, Dentures, Implants,
  Orthodontics, Cosmetic, Repairs, Custom), Pricing Architecture (**Unit-Based** or
  **Fixed-Price**), Default Rate (₹)\*, Description.
- Filter chips by category; search by name/description.
- Each card shows the base rate per unit (unit-based) or standard fixed price.
- Ships with 14 predefined work types (zirconia/PFM/E-max/metal crowns, implant crowns,
  complete/flexible/cast-partial dentures, night guard, retainer, veneer, repairs).

**Connections:** work types drive pricing (default rate + pricing model) for every new
order; clinic-specific rates override them per clinic.

---

## F. Clinic Pricing

**What it does:** per-clinic negotiated rate matrix.

**Workflow:** pick a clinic → the list shows every active work type with its standard
rate and the clinic's custom rate (labelled *Custom Rate* / *Standard Rate*) → edit a
rate in the dialog (rate must be > 0).

**How it applies:** when a new work order is created, the effective rate for
(clinic, work type) = the clinic-specific rate if configured, else the work type's
default. **Past work orders always keep their historical rate** — only future orders
use the new rate.

**Limitations:** you can overwrite a custom rate but not reset it back to "standard"
from the UI; the pricing model itself is defined on the work type, not per clinic.

---

## G. Payments & Billing

**What it does:** clinic-account-level payment tracking — the billing heart of the app.
Payments are recorded **against a clinic's account, never against an individual work
order**: clinics typically settle one combined amount per month.

- **Payments screen:** Total Collected / Total Outstanding summary, per-clinic account
  cards (billed, paid, outstanding) sorted by outstanding, and the full payment history
  ledger with search and delete.
- **Recording a payment** (from the Payments screen, a clinic's detail screen or while
  viewing a bill): clinic, date (date picker), Amount (₹)\*, method chips (**Cash, UPI,
  Bank Transfer, Card, Cheque**), optional Reference / Txn ID and Note. The current
  outstanding is shown as a hint. Multiple payments per clinic are supported.
- **Outstanding balance per clinic** = non-cancelled billed work − all payments for that
  clinic. Cancelled orders are never billed.

**Limitations:** no per-case payment attribution by design; no date/method filters on
the history ledger.

---

## H. Bills, Statements & Reports

### Monthly Bill (per clinic)

Pick clinic + month (month shift buttons, month and year dropdowns). Shows the billing
period, work entries, units, month revenue and the payment summary:
**Remaining Balance = Previous Outstanding + Month Billing − Payments Received**.
Export as **PDF** (printable invoice), **Excel** (3-sheet workbook) or **CSV**, each
offered with Share / Print / Save-to-Downloads. A *Record Clinic Payment* shortcut is
built in.

### Clinic Statement (per clinic)

Like the bill but for any period: **All Time / Monthly / Yearly / Custom Range**, plus a
status filter for the visible work table. Shows statement number
(`ST-<code>-…`), work summary stats, the account summary and the payments received list.
Exports as **PDF** or **Excel**.

### Reports & Analytics

All-time aggregates in three tabs:

- **Financial:** Billed / Collected / Due, collection recovery rate with progress bar,
  and clinics with the highest outstanding dues.
- **Clinics:** per-clinic cases, billed, paid and due.
- **Work Types:** case counts, units and revenue per work type.

**Limitations:** the Reports screen has no period selector (period filtering is on the
Dashboard); there are no charts.

---

## I. Import / Export

**Exports** (XLSX written to a file and then shareable/savable; CSV copied to the
clipboard through a preview dialog):

| Export | Contents |
| --- | --- |
| Work Orders Production Ledger (XLSX/CSV) | all orders — job #, dates, clinic, doctor, patient, work type, teeth, model, units, rate, total, status |
| Clinic Directory & Outstanding Balances (XLSX/CSV) | contacts, active status, order volume, billed/paid/balance |
| Payments & Receipts Ledger (XLSX/CSV) | receipt ID, date, clinic, amount, method, reference, note |
| Monthly Bill (PDF/XLSX/CSV) | per clinic + month |
| Clinic Statement (PDF/XLSX) | per clinic + any period |
| Clinic Work History (XLSX) | one clinic's orders, from the clinic detail screen |
| Warranty Card (PDF) | 2-page CR80 card (see below) |

**Import (CSV, pasted text):** two templates — *Clinics CSV* (Clinic Code, Name,
Dentist, Phone, City) and *Work Orders CSV* (Job Number, Patient Name, Work Type,
Units, Rate, Shade). Tap a chip to load the sample template, paste your rows, then
**Validate & Import**. A result message reports added/skipped rows.

**Limitations:** import requires at least one registered clinic for work orders, and
imported orders are attached to the first registered clinic with default patient /
work-type mapping — it is a bulk-start tool, not a full-fidelity restore. Job numbers
are auto-generated when the CSV cell is blank.

---

## J. Warranty Cards

**What it does:** printable patient warranty cards for a work order, stored in the
database and editable at any time.

**Workflow:**

1. Open a work order's details dialog (Work Orders screen) → **Warranty Card**.
2. The card is created as an editable draft pre-filled from the order, patient, clinic
   and lab settings (card number `WC-YYYY-NNNN`, default 10-year warranty).
3. Edit any field: lab identity, patient name/address/phone, consultant doctor, work
   type/material, tooth numbers, shade, delivery/issue date, warranty period
   (**1, 2, 3, 5, 7, 10, 15 or 20 years** — the expiry date is always recomputed as
   delivery + duration), and the warranty terms text.
4. Preview front/back, **Save Card**, and **Print / Export PDF**.
5. The PDF is **exactly two CR80/ID-1 card-sized pages (85.60 × 54 mm)** — page 1 front
   (title, lab name, card number, patient block, validity badge, work details grid),
   page 2 back (full terms & conditions, lab contact, card info, signature line). It can
   be shared, printed (front/back on card stock) or saved to Downloads.

**Connections:** each card is linked to its work order (one card per order), clinic and
patient; reopening an order's warranty card loads the stored card, never a new draft.

**Limitations:** cards are reached through their work order (no standalone card list
yet); warranty cards are not part of the Google Sheets sync.

---

## K. Settings & Backup

- **Laboratory Identity & Profile:** lab name, phone/WhatsApp, email, address, city,
  default turnaround days, currency symbol — used on bills, statements and warranty
  cards.
- **Google Sheets Sync (optional):** enable, paste your Apps Script Web App URL and an
  optional shared secret, then **Sync Now** (pending changes) or **Full Push**
  (everything). Status line shows pending changes and the last sync time. See
  [Google Sheets Sync Setup](GOOGLE_SHEETS_SYNC_SETUP.md).
- **Offline Database Statistics:** record counts for every entity.
- **Data Safety & Backup:** **Generate Backup** creates a JSON summary snapshot
  (counts, totals and a per-work-order digest) and copies it to the clipboard;
  **Reset Demo Data** reloads the fictional demo dataset (5 clinics, ~140 patients,
  ~300 orders); **Delete ALL Data** empties the database (work types and settings are
  kept).
- The app follows the system light/dark theme.

**Limitations:** the JSON backup is a summary snapshot, not a restorable full export
(see the [roadmap](ROADMAP.md)); there is no in-app restore yet.

---

## L. Dental Odontogram

A 32-tooth interactive chart used in the new-work and edit dialogs (and read-only in the
order details dialog):

- Upper and lower arches, each split into right/left quadrants; teeth displayed as
  single digits **1–8 per quadrant** (FDI numbering is stored internally, e.g. `11,12`).
  Each tooth shows its type abbreviation (CI, LI, C, 1P, 2P, 1M, 2M, 3M).
- Tap teeth to toggle them; quick-select chips (All Upper / All Lower / per quadrant)
  and **Clear All**.
- The selection summary reads like "Upper Right: 1, 2 | Upper Left: 4, 5"; ledgers show
  the compact form (e.g. `UR:1,2`).

---

## M. Optional Google Sheets Sync

Manual, opt-in mirror of business data (clinics, patients, work types, clinic rates,
work orders, payments — including delete tombstones) to a Google Sheet that the user
owns, via an Apps Script web app they deploy themselves. Rows are upserted by `syncId`,
so re-syncing never duplicates. Credentials stay on the device; nothing is compiled into
the APK. Lab settings and warranty cards are not synced. Full setup:
[GOOGLE_SHEETS_SYNC_SETUP.md](GOOGLE_SHEETS_SYNC_SETUP.md).
