# Google Play release checklist

1. Ensure the app compiles and tests pass in GitHub Actions; exercise it on real Android devices.
2. Replace Google test AdMob application/ad-unit IDs with your OWN app and unit IDs; register app in AdMob. Do not publish using test IDs.
3. Configure UMP messages and consent choices, test across relevant regions and add an in-app privacy choices entry where required.
4. Host a completed privacy policy on a publicly accessible URL and provide a developer support email.
5. Complete Google's Data safety, App content, ads declaration and content rating honestly, including AdMob SDK disclosures.
6. Prepare original store icon (512x512), feature graphic (1024x500), screenshots, short/full descriptions and all required listing fields.
7. Set up a protected upload key (never commit private keys); sign the release bundle using Play App Signing workflow.
8. Review the app for data loss, backups/import, accessibility and all device sizes.
9. If your Play personal developer account requires closed testing, complete the required testing period before production application.
10. Upload the signed AAB and submit for Google Play review. Approval is Google's decision.

New apps submitted after 31 August 2026 must target API 36 or higher. This code targets 36.

**Account release gate:** Complete `ACCOUNTS_SETUP.md`, including dedicated project, custom SMTP, public client configuration, deployed deletion function, live email/deletion tests, private support email and final privacy retention details. Publish and test both the in-app deletion flow and `delete-account.html`, then provide the web deletion URL in Play Console. Cloud sync is implemented in the draft account-sync release and requires explicit opt-in. Do not describe it as production-ready until real email/Google login and two-device recovery/conflict tests pass.

**Current limitations:** Notifications are not implemented. The regular CI release AAB is unsigned. A separately gated workflow can produce a signed production AAB once Play signing and real AdMob secrets are configured; production ads and final policy details still need configuration. Store captures from 0.2.x predate the Account tab; recapture before publication.

## Protected signed Play AAB workflow

The optional [signed Play bundle workflow](../.github/workflows/play-bundle.yml) runs only when manually started from `main`. It **fails closed** unless the following GitHub Actions repository secrets are set: `MONEYMATE_RELEASE_KEYSTORE_BASE64` (PKCS12 keystore, base64 encoded), `MONEYMATE_RELEASE_STORE_PASSWORD`, `MONEYMATE_RELEASE_KEY_ALIAS`, `MONEYMATE_RELEASE_KEY_PASSWORD`, `MONEYMATE_ADMOB_APP_ID`, and `MONEYMATE_ADMOB_BANNER_ID`. Do **not** add signing secrets to files, chat messages, commits, or issue reports.

It runs unit tests, signs an AAB using the private Play upload key, verifies the JAR signature and provides an artifact. The existing ordinary CI runs with Google's test AdMob IDs and an unsigned release AAB. A signed bundle is **not automatically submitted** to Google Play. Any key changes must respect Google's Play App Signing/upload key reset processes. The production build must be reviewed and tested before internal/closed/production release. No credentials are created or charged by this workflow.
