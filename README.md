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
- On-device SQLite storage with optional Supabase accounts and separate local profiles
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

Financial records stay on the phone unless exported. Account email, credentials and security events are processed by Supabase and the configured email provider. AdMob can transmit device/ad-related information to Google; sample ad IDs MUST be replaced before release. See [privacy notice](docs/PRIVACY.md) and [release checklist](docs/PLAY_RELEASE.md).

## Not yet included

This is an **MVP**, not a finished Play Store submission. Push notifications and CSV import, production AdMob/UMP setup and release signing still need further development. See [0.2.0 test update and safe installation](docs/TEST_UPDATE.md) for the visual update, store graphics and signing limitations. The Play Console review and signing key also remain outstanding. Never commit secrets or signing keys.

## Application ID

`nz.moneymate.app`
## Accounts (0.3.0)

Optional Supabase email/password accounts with email-code confirmation, reset, sign-out, encrypted session storage, separate offline local profiles and account deletion. Existing local records are preserved. Accounts do not sync financial records. See [account setup](docs/ACCOUNTS_SETUP.md) for exact project, SMTP, public build configuration, deletion deployment and Play publication requirements. Blank configuration keeps local budgeting available.
