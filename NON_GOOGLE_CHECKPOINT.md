# Non-Google continuation of PR #30

This continuation leaves the existing Google OAuth flow, chooser, configuration and credentials unchanged. Earlier Google changes already present in PR #30 remain in the draft.

## Changes

- Android no longer deletes saved encrypted credentials when session loading fails. The account remains locked until credentials can be read or the user signs in again; explicit sign-out still clears the vault.
- Added Android email/password reopening, offline retention and refresh-token rotation/reopening coverage, plus unreadable-vault preservation coverage.
- Android ANZ PDF parsing now rejects impossible calendar dates instead of silently adjusting them. Added matching browser and Java regression cases, including a valid leap day.
- Added a browser sync integration regression covering all five record collections, updates and deletions through a simulated revision-checked cloud service.
- Web CI now runs the complete npm test suite, including account-deletion endpoint tests, and triggers for endpoint/dependency changes.

## Local validation

- 86 JavaScript tests pass.
- 23 Java unit tests pass: MoneyMathTest, BankCsvTest, PdfStatementTest and AnzStatementTest, compiled with Java 17 and run with JUnit 4.13.2. This is not a full Android Gradle build.
- git diff --check passes.

## Remaining verification

- New Android instrumentation tests require CI/emulator execution; the local machine has no configured Android SDK/Gradle toolchain.
- Simulated transport and profile tests do not prove production email delivery or live cross-device sync. Verify real email/password login, Android process reopening, and web/Android round trips of transactions, budgets, goals and bills before release.
- Verify an actual ANZ PDF and OCR extraction on the device, including import preview and duplicate detection. Current import tests use synthetic extracted statement text.
- Cloud synchronization remains opt-in and stops on conflicting edits, preserving both copies. No automatic conflict merging was introduced.

No Supabase configuration, auth URL, live database, user records, secrets, testers, billing, domain or paid services were changed. This draft is not merged or deployed.
