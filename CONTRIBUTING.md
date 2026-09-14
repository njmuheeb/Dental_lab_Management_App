# Contributing to Dental Lab Management

Thank you for your interest in improving this project. This document explains how to
report problems, propose features, and submit changes.

## Reporting a bug

1. Check the [existing issues](https://github.com/njmuheeb/Dental_lab_Management_App/issues)
   to avoid duplicates.
2. Open a new issue using the **Bug report** template
   (`.github/ISSUE_TEMPLATE/bug_report.md`).
3. Include exact steps to reproduce, what you expected, and what happened.
4. **Never attach real patient, clinic, or personal data.** Blur or anonymize screenshots.

## Requesting a feature

1. Open an issue using the **Feature request** template
   (`.github/ISSUE_TEMPLATE/feature_request.md`).
2. Describe the workflow problem you are trying to solve, not only the solution.
3. For larger changes, please discuss before investing significant time in a PR.

## Submitting changes

1. Fork the repository and create a branch from `main`:
   `git checkout -b feat/short-description`
2. Make your change with the smallest clean diff possible — this project intentionally
   avoids rewrites and unnecessary dependencies.
3. Run the verification commands below and make sure everything passes.
4. Open a pull request using the template at
   `.github/PULL_REQUEST_TEMPLATE.md`.

### Verification commands

```bash
# Full unit test suite (Robolectric / JUnit)
./gradlew :app:testDebugUnitTest

# Build the debug APK
./gradlew :app:assembleDebug

# Build everything and run all checks
./gradlew build
```

On Windows use `gradlew.bat` instead of `./gradlew`.

> **Windows note:** if unit tests fail with *"Unable to load Robolectric native runtime
> library"*, the build is configured to use an offline Robolectric dependencies folder
> (`C:/Android/robolectric-deps` by default) because Robolectric cannot handle paths
> containing spaces. See `docs/DEVELOPMENT.md` for details.

## Code expectations

- **Kotlin, official code style** (`kotlin.code.style=official` in `gradle.properties`).
- **UI**: Jetpack Compose + Material 3. Keep screens declarative; reuse the existing
  components in `ui/components/`.
- **Architecture**: MVVM. UI → `DentalLabViewModel` → `DentalLabRepository` → Room DAOs.
  All database access must go through the repository so integrity guards and sync flags
  stay consistent.
- **Money**: never do arithmetic on amounts ad hoc — use `MoneyUtils`
  (`round2`, `totalFor`, formatting).
- **PDF export**: the PDF generators use base-14 fonts (no rupee glyph, ASCII-only
  output). Keep exported documents ASCII-safe.
- **Database changes**: if you change any Room entity, bump the schema version in
  `AppDatabase` **and add a migration** — the app must never lose user data. Update
  `docs/DATABASE.md` accordingly.
- **Tests**: add or update unit tests for the logic you touch (the existing suite lives
  in `app/src/test/java/com/example/`).
- **No secrets, no real data**: never commit API keys, tokens, passwords, or real
  patient/clinic information.

## Commit message style

Use short, imperative, conventional-commit-style messages:

```
feat: add delivery-date filter to work orders
fix: wrap long patient names on warranty card PDF
docs: update database documentation
```

## Documentation

If your change alters user-visible behaviour, update the relevant documents:

- `README.md` — features overview
- `docs/FEATURES.md`, `docs/USER_GUIDE.md` — behaviour and workflows
- `docs/DATABASE.md` — schema or migration changes
- `CHANGELOG.md` — add a line under **Unreleased**

## License

By contributing, you agree that your contributions will be licensed under the
[MIT License](LICENSE).
