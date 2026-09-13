# Google Sheets Sync (Optional) — Nazneen Dental Lab

The app is **100% offline-first**. Room/SQLite on the device is the only source of truth and
every feature (dashboard, clinic details, statements, bills, PDF/XLSX/CSV exports) works with
no internet connection. Google Sheets sync is an **optional mirror** you can enable later.

## Architecture

| Concern | Implementation |
| --- | --- |
| Local data | Room database (`nazneen_dental_lab.db`), version 2 — primary source |
| Unique IDs | `syncId` (UUID) on every clinic, patient, work type, clinic rate, work order and payment |
| Change tracking | `pendingSync` flag set on every write; `updatedAt`/`createdAt` timestamps |
| Deletes | Tombstone entries in the `sync_queue` outbox table (a deleted row cannot carry a flag) |
| Dedup | The Apps Script upserts **by `syncId`**, so re-syncing updates rows in place — no duplicates |
| Secrets | Web App URL + shared secret are entered by the user in Settings and stored in the local DB. Nothing is compiled into the APK |
| Sync trigger | Manual only: "Sync Now" (pending changes) or "Full Push" (everything) in Settings |

## Setup

1. Create a Google Sheet, e.g. *Nazneen Dental Lab Backup*.
2. **Extensions → Apps Script**, paste the contents of
   [`docs/google-apps-script/nazneen-dental-sync.gs`](google-apps-script/nazneen-dental-sync.gs).
3. Change the `SECRET` constant at the top of the script to a long random string.
4. **Deploy → New deployment → Web app**:
   - *Execute as:* **Me**
   - *Who has access:* **Anyone**
   - Copy the `https://script.google.com/macros/s/.../exec` URL.
5. In the app: **Settings & Backup → Google Sheets Sync (Optional)**:
   - Enable the toggle
   - Paste the Web App URL
   - Paste the same `SECRET`
   - Tap **Sync Now** — the first sync pushes the full dataset; later syncs push only changes.
6. Sheets named *Clinics*, *Patients*, *Work Types*, *Clinic Rates*, *Work Orders* and
   *Payments* are created/updated automatically.

## Notes

- If a sync fails (offline, wrong URL, bad secret), nothing is lost: pending flags and the
  tombstone queue stay intact and the next successful sync picks them up.
- Lab settings (including the sync URL/secret itself) are intentionally **not** synced.
- To move data to a new device, use Settings → Generate Backup (JSON snapshot) or export the
  ledgers from Import/Export.
