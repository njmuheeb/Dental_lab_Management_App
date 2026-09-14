# Roadmap

This roadmap describes **planned or considered** future work. None of the items below are
implemented yet — everything currently available in the app is documented in
[FEATURES.md](FEATURES.md).

## Status legend

| Marker | Meaning |
| --- | --- |
| 🟡 Planned | Intended for an upcoming release |
| 🔵 Under consideration | Being evaluated; not committed yet |

## Near term (planned)

- 🟡 **File-based backup & restore** — save the JSON backup directly to a file / cloud
  folder and restore it on the same or another device (today the backup snapshot is
  copied to the clipboard and there is no in-app restore).
- 🟡 **Improved PDF customization** — lab logo on bills/statements/warranty cards,
  selectable document colors, and editable warranty terms templates.
- 🟡 **Expanded reporting** — date-range revenue reports, per-work-type and per-doctor
  breakdowns, and exportable dashboard summaries.

## Medium term (planned / under consideration)

- 🔵 **Multi-device synchronization** — a first-class sync backend beyond the optional
  Google Sheets mirror (today the app is strictly single-device).
- 🔵 **Batch printing** — print multiple warranty cards or monthly bills for several
  clinics in one run.
- 🔵 **Delivery reminders** — notifications for work orders approaching or past their
  expected delivery date.
- 🔵 **Low-resolution device refinements** — denser table layouts for small screens.
- 🔵 **Localization** — the UI is currently English-only; translations may follow.

## Long term (under consideration)

- 🔵 Cloud backup service (user-hosted, keeping the offline-first promise).
- 🔵 Photo attachments per work order (case photos, shade references).
- 🔵 Barcode/QR workflow for job bags and warranty card verification.
- 🔵 Inventory & material tracking.

## Engineering goals

- 🟡 Continuous Integration via GitHub Actions (build + unit tests on every push).
- 🟡 Increase unit-test coverage of repositories and export generators.
- 🔵 Instrumented (on-device) test suite for the PDF print pipeline.

---

Nothing on this roadmap is a commitment. If you would like to work on one of these items,
open a feature request first (see
[CONTRIBUTING.md](../CONTRIBUTING.md)) so the approach can be discussed.
