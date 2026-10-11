# MoneyMate NZ 0.2.0 test update

This update adds the wallet launcher icon, Android 12+ branded launch screen, emerald/cream cards, permanent section navigation, welcome and empty illustrations, category spending donut and percentages, budget remaining amounts, savings progress, and clearer transaction cards.

## Protect your current records first

1. Open the **current** installed app. Go to **Export**.
2. Choose **Backup all data (JSON)** and save outside the app (for example Downloads). Keep a second copy somewhere secure.
3. Confirm the file exists and is not empty. Keep it until you have checked restored transactions, budgets, goals, bills and recurring schedules.
4. Try installing the new APK over the current app. If Android accepts it, open the app and check your records.
5. If Android reports a signature conflict / app not installed, do not uninstall until your full JSON backup is safe. After that, uninstall the old test app, install the new APK, then use **Export > Restore full backup** and select the JSON file. Restore replaces all records in the new installation.

CSV is useful for viewing transactions, but it is not a complete backup. Never uninstall without the full JSON backup. No data is automatically transferred to a new signing key.

## Signing findings

The application ID is still `nz.moneymate.app`. The SQLite database is still `moneymate.db`, schema version 2. App version is 0.2.0, version code 2.

Earlier CI generated ephemeral debug keys and did not preserve their private keys. The previous main build (run 38181696118) has certificate SHA256:

`fbe5b3b8c1c08a35402409f08b574164e68472eb4e1d85e8c89b52e05947898d`

The new persistent **test-only** certificate is:

`7bf330320f1084bed0f590db25269ad8559d3195a85ff3a1a4c442ed3d0f664c`

These certificates differ. Android will reject an in-place update from that previous build. Your phone's installed APK has not been inspected, so compatibility with your exact installation is not verified. Other earlier CI APKs also used ephemeral keys.

The new private test key is stored only as the encrypted Actions secret `MONEYMATE_TEST_KEYSTORE`. CI restores it and verifies the APK certificate. Future test builds using that secret and increasing version codes can retain signing compatibility. Do not delete/replace the secret. This is not a Play production signing key.

## Verification and limits

CI runs the money unit tests, builds a debug APK and unsigned release AAB, installs the app on an Android 15 emulator, opens the empty and sample-data screens, navigates all sections, verifies transaction totals and performs a full JSON backup/restore round trip including budgets, goals, bills and recurring entries. Screenshots use fabricated financial data. A debug-only capture flag hides the sample advertising banner while taking store images; normal app launches still follow the existing consent and ad flow. An actual upgrade of your phone has not been tested.

The release AAB remains unsigned and the app still uses Google's sample ad identifiers. These outputs are for testing, not a complete Play release.

## Store assets

`store-assets/` contains a 512x512 PNG icon, a 1024x500 opaque PNG feature graphic, and promotional phone images composed from actual emulator screenshots. Capture provenance and alt text are recorded in `asset-manifest.json`. They follow [Google Play preview asset guidance](https://support.google.com/googleplay/android-developer/answer/9866151?hl=en).

Recreate graphics with Pillow:

```
python tools/store_assets.py --screenshots screenshots --out store-assets
```

Fonts: Segoe UI on Windows, DejaVu Sans on Linux. The promotional example amounts are not promises of financial outcomes. Store graphics do not change the installed app.
