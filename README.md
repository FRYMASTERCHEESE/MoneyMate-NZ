# MoneyMate NZ 💚

A native, offline-first Android budget tracker designed for New Zealand.

## Implemented in this first release candidate

- Track income and expenses, with date, category and notes
- NZD monetary amounts stored as integer cents (no floating-point currency errors)
- Monthly balance summary and category totals
- Monthly category spending limits and progress bars
- Savings goals with contributions
- Dated bills list with upcoming bills dashboard
- Transaction deletion with confirmation
- CSV transaction export through Android's file picker
- On-device SQLite storage; no account or paid backend
- Google AdMob sample ads gated by Google UMP consent eligibility
- Android API 36 target, CI build workflow and JUnit money tests

## Get the test build

Visit [GitHub Actions](https://github.com/FRYMASTERCHEESE/MoneyMate-NZ/actions), open a successful **Android CI** run and download the **moneymate-android-build** artifact. The debug APK is for testing. The release AAB is **unsigned**, not Play-upload ready.

## Developer build

Android SDK platform 36, JDK 17 and Gradle 8.13 are required. Run:

```bash
gradle :app:testDebugUnitTest :app:assembleDebug :app:bundleRelease
```

CI provisions Gradle and Android SDK automatically.

## Privacy and advertising

Data stays on the phone unless a user explicitly exports it. AdMob can transmit device/ad-related information to Google; the sample advertising IDs MUST be replaced before release. See [privacy notice draft](docs/PRIVACY.md) and [release checklist](docs/PLAY_RELEASE.md).

## Not yet included

This is an **MVP**, not a finished Play Store submission. Recurring entries, push notifications, comprehensive backup/restore, CSV import, richer graphs, fully styled UI, production AdMob/UMP setup, screenshots and store graphics need further development. The Play Console review and signing key also remain outstanding. Never commit secrets or signing keys.

## Application ID

`nz.moneymate.app`
