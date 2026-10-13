# MoneyMate NZ 💚

**Status (13 October 2026): working website and Android test app; not certified Play release ready.**

- Live web: https://frymastercheese.github.io/MoneyMate-NZ/
- Public [step-by-step website and Android guide](https://frymastercheese.github.io/MoneyMate-NZ/help.html)
- Source: `FRYMASTERCHEESE/MoneyMate-NZ`
- Android application ID: `nz.moneymate.app`; next test version **0.3.6**, version code **10**.

## Implemented and automated-test coverage

- Home dashboard, income/expenses, category charts, budgets, savings goals, bills, repeating transactions and NZD integer-cent amounts.
- Android private SQLite + web browser IndexedDB storage; guest profiles and signed-in owner-isolated local profiles.
- Supabase Google/email authentication, session persistence code, login/recovery/deletion controls, and **optional opt-in** Android ↔ website financial snapshot sync using server-enforced revisions. Two real devices and real accounts are **still required** to validate cloud sync end to end. Turning on cloud sync copies the chosen signed-in profile to Supabase; backups are essential before using cloud restore.
- MoneyMate Points removed from current clients; historic points data remains preserved. A separate revocation migration can disable old reward RPCs after new clients are live. The read-only admin counts dashboard is unchanged.
- Full JSON backup and recovery plus CSV export. **JSON restore REPLACES** the selected profile's records. Always back up first.
- Fee-free, manual **NZD bank CSV statement import** on web and Android, with review and duplicate checks.
- **Experimental, manual NZD bank PDF import**: browser PDF.js text extraction, Android on-device OCR, a dedicated ANZ NZ statement parser and a conservative general parser for compatible bank layouts. Every recognised PDF transaction requires review. Ambiguous statements fail safely; a bank name does NOT mean all PDFs from that bank are supported. No bank passwords or paid providers needed.
- Android API 36, UMP/AdMob **test ads**, CI builds, web tests, Android unit and emulator tests.

## Download the Android test build

Open [Android CI](https://github.com/FRYMASTERCHEESE/MoneyMate-NZ/actions/workflows/android.yml), select a **successful `main` workflow run**, download the `moneymate-android-build` artifact, extract it and install the **debug APK**. Only install builds from the official repository. Keep the app installed and **save a private JSON backup before upgrading**; never clear app data or uninstall merely to get an update. Debug builds are **not** Play Store releases. The CI-generated release AAB is unsigned.

## Build locally

Requires Android SDK platform 36, JDK 17 and Gradle 8.13:

```bash
gradle :app:testDebugUnitTest :app:assembleDebug :app:bundleRelease
node --test tests/*.test.mjs
```

Run `npm ci --ignore-scripts` first for the browser storage emulator used by regression tests. Version 0.3.5 adds atomic browser sync/restore guards, category-independent statement duplicate checks, exact CSV export reimports, stricter Android backup validation, and server-side snapshot validation. No customer records or local database schemas are rewritten. Google sign-in configuration is unchanged.

## What is not yet verified or enabled

1. **Real-world end-to-end testing:** Google/email signup and recovery, same-account sync and conflict handling on an actual Android device + browser, signed-in account deletion, CSV/PDF statements on real bank layouts, account isolation and data restoration. Automated tests are not a substitute.
2. **All New Zealand bank PDFs:** ANZ NZ synthetic layout fixtures pass, but a real, safely redacted ANZ PDF is required to claim that ANZ's actual statement layout is supported; each other bank's PDF format must be checked. Scanned website PDFs are unsupported without OCR. Android OCR can make mistakes.
3. **Automatic bank linking:** Still not live; full Akahu/Basiq onboarding, regulated access arrangements, security, consent and commercial pricing remain external blockers. The fee-free CSV/PDF import is manual only. AUD transaction imports remain blocked to avoid mixing Australian dollars into NZD budgets.
4. **Production AdMob and Play bundle:** Real AdMob app ID + unit ID and consent setup, private upload keystore, support email, signed release AAB and final Play declarations/graphics are still required. Protected workflow `.github/workflows/play-bundle.yml` is a manual signing path only when owner secrets are provided.
5. **Live backend readiness:** Supabase currently has owner RLS and guarded financial snapshot RPC; keep the source private. Security advisor reports **leaked-password protection disabled** (enable under Supabase Auth security settings if eligible) and intentional restricted security-definer functions requiring review. Privacy policy still needs final SMTP-provider disclosure, retention periods and monitored support contact.
6. **No claim of 100% certification or guaranteed Play approval.** Details: [Play release checklist](docs/PLAY_RELEASE.md), [account setup](docs/ACCOUNTS_SETUP.md), [sync safety](docs/SYNCING.md).

## Privacy

Do not commit bank statement files, API credentials, login tokens, signing keys, private user exports or Supabase service-role keys. Public client configuration in `docs/account-config.json` is intentionally publishable only. See [privacy policy](https://frymastercheese.github.io/MoneyMate-NZ/privacy.html). MoneyMate NZ does not move money, make bank payments or provide financial advice.
