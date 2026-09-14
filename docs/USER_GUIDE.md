# User Guide

A step-by-step guide to using **Dental Lab Management** for daily dental laboratory
work. No technical knowledge is required.

> The app works completely **offline** — all your data is stored on this device/tablet.
> No account, internet connection, or subscription is needed. Internet is only used if
> you switch on the optional Google Sheets backup mirror.

## 1. Opening the application

1. Tap the app icon on your device.
2. The **Dashboard** appears first. Use the menu button (top-left) to open the side
   drawer, or the bottom bar to jump between **Dashboard**, **Work Orders**,
   **+ New** (new work entry), **Clinics** and **Payments**.
3. On first launch the app is pre-loaded with a **demo dataset** (fictional clinics,
   patients and orders) so you can explore. You can delete it any time in
   **Settings & Backup → Delete ALL Data**.

> **Tip:** On the Dashboard, the period chips (All Time / Today / This Week /
> This Month) change what the Revenue / Collected / Outstanding cards count.

## 2. Setting up your lab details

1. Open the drawer → **Settings & Backup**.
2. Under **Laboratory Identity & Profile**, enter your lab name, phone, address and
   city. These details appear on your bills, statements and warranty cards.
3. Set the **Default Turnaround (Days)** — new work orders will suggest this delivery
   time — and your currency symbol.
4. Tap **Save Lab Settings**.

## 3. Adding a clinic / doctor

1. Open **Clinics / Doctors** (drawer or bottom bar).
2. Tap the **+ Add Clinic** button.
3. Fill in at least the **Clinic Name** and **Dentist / Doctor** (phone, city, address
   and notes are optional).
4. Tap **Save Clinic**.

Repeat for every clinic you serve. To edit later, tap the pencil icon on the clinic
card; tap the card itself to open the clinic's full detail page.

> **Note:** a clinic can only be deleted when it has no work orders and no registered
> patients — this protects your history.

## 4. Adding a patient

1. Open **Patients** from the drawer.
2. Tap the **+** button.
3. Enter the **Patient Full Name**, choose their **Associated Clinic**, and optionally
   phone, age, gender and notes.
4. Tap **Save**.

You can also add a patient on the fly while creating a work order (step 5) — tap the
**+** button next to the patient dropdown.

## 5. Creating a work order (a new lab job)

1. Tap **+ New** in the bottom bar.
2. **Choose the clinic** — pick it from the dropdown (or tap **+** to add a new clinic
   right here). The dentist name fills in automatically.
3. **Choose the patient** — the list shows the patients of that clinic. Tap **+** to
   register a new one. Optionally enter the clinic's **Case / Ref #**.
4. **Choose the work type** (e.g. Zirconia Crown). The price is filled in automatically:
   - if you have set a special rate for this clinic (see section 7), that rate is used;
   - otherwise the standard rate of the work type is used.
   You can still adjust the rate and the units manually. Toggle **Unit/Fixed** if the
   job is priced per tooth or as one flat price.
5. **Select the teeth** on the tooth chart: tap each tooth, or use the quick buttons
   (All Upper, All Lower, quadrant buttons). With unit-based pricing the units count
   follows your tooth selection automatically.
6. **Choose shade and material** from the dropdowns (or type your own), pick the
   **turnaround** (3/5/7/10 days) and add any **special instructions** for the
   technician.
7. Check the **TOTAL** preview and tap **Save Work Order** (top right).

The job number (like `NDL-2026-0042`) is generated automatically and the new order
appears in **Work Orders** with status **Received**.

## 6. Tracking and updating work status

1. Open **Work Orders**.
2. Find the order using the search box or the status filter chips, and tap it to see
   full details.
3. Tap **Change Status** and choose the new status: Received, Pending, In Progress,
   Ready, Completed, Delivered or Cancelled.
4. Tap **Save**.

The Dashboard's **Production Pipeline** counters and the urgent-delivery list update
automatically.

> To **edit** a full order (patient, teeth, shade, rate, delivery, status, notes): open
> **Clinics / Doctors** → tap the clinic → find the order in the **Work History** table
> → tap the row (or its edit icon).

## 7. Applying clinic-specific pricing

If a clinic has negotiated different rates:

1. Open **Clinic Pricing** from the drawer.
2. Select the clinic at the top.
3. Find the work type in the list and tap the pencil icon.
4. Enter the agreed rate and tap **Save Rate**.

From now on, **new** work orders for that clinic use the special rate automatically.
Existing orders keep the rate they were created with, so your past billing never
changes.

## 8. Recording a payment

Clinics pay at the **account level** — one combined amount, typically monthly. You never
attach a payment to a single job.

1. Open **Payments & Billing** and tap the **Record Clinic Payment** button
   (or open a clinic's page and tap **Record Payment**).
2. Select the clinic. The screen shows their current outstanding balance as a hint.
3. Enter the **Amount**, check the **date**, pick the method (**Cash, UPI, Bank
   Transfer, Card, Cheque**) and optionally add a reference number (e.g. UPI txn ID)
   or note.
4. Tap **Confirm Payment**.

The clinic's outstanding balance drops immediately. You can review or delete any
receipt in the payment history list.

## 9. Viewing outstanding balances

- **Per clinic:** open the clinic's page (Clinics → tap the clinic) — see Work Entries,
  Total Revenue, Payments Received and Outstanding; or check the Payments screen's
  **Clinic Accounts** cards.
- **All clinics:** the Dashboard's **Outstanding** card (for the selected period) and
  **Reports & Analytics → Financial** tab (all-time Billed / Collected / Due, recovery
  rate, and clinics with the highest dues).

## 10. Generating bills, statements and reports

**Monthly bill (invoice) for one clinic:**

1. Open the clinic's page → **Generate Bill**.
2. Pick the month (use the ◀ ▶ buttons or the dropdowns).
3. Review the period's works, payments and the remaining balance.
4. Tap **Bill PDF**, **Excel** or **CSV**, then choose **Share**, **Print** or
   **Save to Downloads**.

**Statement for any period:**

1. Open the clinic's page → **View Statement** (or the **Statement** button in the top
   bar).
2. Choose **All Time**, **Monthly**, **Yearly** or **Custom Range** (pick From/To
   dates).
3. Export as **Statement PDF** or **Excel**.

**Reports:** open **Reports & Analytics** and switch between the **Financial**,
**Clinics** and **Work Types** tabs (all-time figures).

## 11. Creating and printing warranty cards

Warranty cards live in their own screen: open the side drawer → **Warranty Cards**.

**Create a card:**

1. Tap **Create New Warranty Card**.
2. Choose how to start:
   - **From Work Order** — search and tap a work order (orders that already have a
     card are not listed). The card opens pre-filled with the patient, work and lab
     details.
   - **Manual Entry** — optionally pick a clinic and a patient so the card is linked
     to your records, then fill the rest by hand in the editor.
3. In the editor, check or adjust: patient name/address/phone, consultant doctor, work
   type, material, tooth numbers, shade, delivery date, warranty period (1–20 years —
   the expiry date is recalculated automatically), the printed terms and the care
   recommendations. *Additional notes* stay on record but are never printed.
4. Tap **Save Card**.

**Preview, print and export:**

1. Use the **Front / Back** chips in the editor (or **View** on a list row) to preview
   both sides — the preview shows the actual saved data.
2. The preview dialog offers **Generate PDF**, **Print** and **Close**; the list rows
   also have direct **PDF** and **Print** buttons. You can still open a card from a
   work order's details dialog (**Warranty Card** button).
3. The PDF contains **exactly two card-sized pages** (85.6 × 54 mm — Aadhaar/CR80 card
   size): page 1 is the front, page 2 is the back.
4. When printing, choose **front/back** printing on card stock and set the print
   scaling to **Actual Size / 100%** — do not "fit to page", or the card will be
   rescaled to the paper size.

**Find cards:** use the search box (patient, clinic, card number or work order
number) or the clinic / delivery-year / Active-Expired filters. Deleting a card asks
for confirmation and cannot be undone.

**Default warranty period:** Settings & Backup → *Default Warranty Period (Years)*
(10 years by default) is applied to every new card.

## 12. Import / Export

**Export ledgers** (drawer → **Import / Export**):

1. Under *Export Data*, choose **Work Orders Production Ledger**, **Clinic Directory &
   Outstanding Balances** or **Payments & Receipts Ledger**.
2. Tap **Excel** to create an `.xlsx` file (then Share or Save to Downloads), or
   **CSV** to copy the text to the clipboard (paste it into Excel / Google Sheets).

**Import data from CSV text:**

1. Under *Import Batch Data (CSV)*, tap **Clinics CSV** or **Work Orders CSV** — a
   sample template is loaded into the text box showing the expected columns.
2. Replace the sample rows with your own rows, keeping the header line.
3. Tap **Validate & Import CSV Records** and read the result message.

> The CSV import is a bulk-start tool: imported work orders are attached to your first
> registered clinic with sensible defaults, and you can correct details afterwards in
> the work order editor.

## 13. Backup (as available today)

- **Ledger exports** (section 12) are the practical way to keep offline copies of your
  data — save them to Downloads, cloud drives, or email them to yourself.
- **JSON snapshot:** Settings & Backup → **Generate Backup** creates a summary of your
  database (counts, totals, and a per-work-order digest) and copies it to the clipboard
  as JSON — useful as a quick point-in-time record.
- **Optional Google Sheets mirror:** in Settings & Backup, enable *Google Sheets Sync*
  and follow [GOOGLE_SHEETS_SYNC_SETUP.md](GOOGLE_SHEETS_SYNC_SETUP.md) to set up your
  own Google Sheet as an automatic mirror. Sync runs only when you tap **Sync Now** or
  **Full Push**.
- **Demo data:** **Reset Demo Data** reloads the fictional sample dataset;
  **Delete ALL Data** empties the database (your work types and lab settings are kept).

> **Please note:** there is currently no one-tap *restore from backup* inside the app —
> a file-based backup/restore is on the [roadmap](ROADMAP.md). Until then, treat the
  exports above as your archive.

## 14. Daily routine at a glance

| When | Do this |
| --- | --- |
| Morning | Check the Dashboard for urgent deliveries and the pipeline |
| Case arrives | **+ New** → clinic, patient, work, teeth → Save |
| Work moves on | Open the order → **Change Status** |
| Case delivered | Set status **Delivered** → issue the **Warranty Card** if wanted |
| End of month | Clinic page → **Generate Bill** → **Record Payment** for what was paid |
| Whenever | **Reports & Analytics** for an overview; export ledgers for your records |
