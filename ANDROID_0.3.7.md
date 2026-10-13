# Android 0.3.7 (11): release optimisation

Release builds use R8 full mode (AGP 8.13.2 default), the optimising Android
ProGuard defaults, and integrated resource shrinking. App JSON/SQLite formats
and signing configuration are unchanged. Library consumer rules protect Ads,
UMP and bundled ML Kit entry points. Avoid blanket `-keep` rules or disabling
optimisation/obfuscation; add targeted rules only for a demonstrated runtime need.

CI runs debug and release unit tests, release lint, debug APK and unsigned release
AAB builds, all JavaScript regressions, and the existing instrumentation suite
against debug and an optimised `nz.moneymate.app.r8test` package on fresh emulators.
The test variant has the same R8 settings as release and uses debug signing only.
Test-only keep rules retain APIs called directly from the separate test APK and
shared tracing/Kotlin runtime APIs. Method-body optimisation remains enabled,
but this variant cannot verify removal/renaming of those retained APIs: use the
actual release mapping/AAB for optimisation evidence, and manually smoke-test
the exact release APK before production distribution.

Existing tests cover budgeting, profile isolation, encrypted sessions, mocked
sign-in/recovery/logout, revision conflicts, CSV imports, parser fixtures, and
JSON backup/restore. They reset synthetic databases: never run them on a personal
device containing records. Real Google/email sign-in and multi-device live sync
remain manual staging-account checks; fixtures do not certify every bank PDF or
the complete document-picker/OCR flow.
The additional generated PDF instrumentation fixture exercises bundled OCR,
transaction import, duplicate detection, and JSON restore on both build variants.

Full release lint currently reports 10 pre-existing errors (nine numeric layout
orientation constants and Android 16 back-navigation handling) and 17 warnings.
CI reports lint separately with `continue-on-error` so these existing findings do
not prevent runtime verification. A green workflow does not mean lint is clean.

With JDK 17, Gradle 8.13 and Android SDK 36 installed:

```sh
npm ci --ignore-scripts
npm test
gradle :app:testDebugUnitTest :app:testReleaseUnitTest :app:assembleDebug :app:bundleRelease :app:lintRelease
gradle :app:connectedDebugAndroidTest
gradle -PmoneymateTestBuildType=r8Test :app:connectedR8TestAndroidTest
python tools/r8_report.py
```

Archive release `mapping.txt`, `configuration.txt`, `usage.txt`, and the R8 summary
with each AAB. Mapping class counts prove renaming took place; they are **not**
Google Play's DEX percentages. Google documents a 25% minimum for each of
obfuscation, optimisation and shrinking from February 2027 for apps above 10 MB
DEX: https://support.google.com/googleplay/android-developer/answer/17492799
Check the exact signed bundle's metrics in Play Console after owner approval.
No upload, publishing, website deployment, backend migration, or production key
change is part of this change.
