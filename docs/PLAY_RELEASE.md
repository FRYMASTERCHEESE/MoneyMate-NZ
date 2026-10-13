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

**Account release gate:** Complete `ACCOUNTS_SETUP.md`, including dedicated project, custom SMTP, public client configuration, deployed deletion function, live email/deletion tests, private support email and final privacy retention details. Publish and test both the in-app deletion flow and `delete-account.html`, then provide the web deletion URL in Play Console. Financial records remain locally isolated by account unless the user opts into guarded account snapshot sync, which requires live two-device verification before production.

**Current limitations (October 2026):** The automatic bank provider links are not live. PDF imports are experimental and need bank-by-bank validation. Push notifications are not implemented (not required for core offline budgeting). The normal release AAB is unsigned and not upload-ready. Production ads, monitored support contact, SMTP provider/privacy disclosures, real-device signup/login/sync/points tests and signing secrets still need configuration. Recapture screenshots of the current v0.3.4 app before publication.

## Signed production AAB (protected manual release)
`.github/workflows/play-bundle.yml` produces a signed Play App Bundle **only** from main, when manually triggered and when privately configured with these GitHub Actions repository secrets:

- `MONEYMATE_RELEASE_KEYSTORE_BASE64` (the developer's PKCS12 upload keystore, base64-encoded)
- `MONEYMATE_RELEASE_STORE_PASSWORD`, `MONEYMATE_RELEASE_KEY_ALIAS`, `MONEYMATE_RELEASE_KEY_PASSWORD`
- `MONEYMATE_ADMOB_APP_ID`, `MONEYMATE_ADMOB_BANNER_ID` (real IDs, not Google's testing IDs)

The workflow runs tests, validates the production ad identifiers, signs the `.aab`, and verifies its signature before uploading the artifact to private GitHub Actions storage for 7 days. It never automatically publishes to Google Play or charges for external services. Don't store private signing keys in the repository, commit history, issues, or chat. Local/debug CI continues with test AdMob IDs and an unsigned release AAB, independently of this manual action.

## Pre-upload reality check (13 October 2026)

- The website and Android can be feature-complete for *offline NZD manual budgeting* without paid bank connectivity; this is different from having a certified, fully tested financial service.
- Verify the production SMTP sender and live signup/verification/password reset; verify account deletion on Android and web with test accounts. Account deletion is destructive: only delete expendable test accounts.
- Test cloud opt-in with an Android phone and separate website session, using fresh backups on both; test the protected conflict screen, signed-in owner isolation, automatic sync and first cloud snapshot creation. The database snapshot table being present does not prove a real two-device flow occurred.
- Test a redacted representative ANZ statement on both platforms. Compare *every* date, credit/debit, running balance and description before saving. Repeat with other banks only after confirming their exact layouts.
- Verify real AdMob app and banner identifiers in the private GitHub Actions environment and UMP privacy flow; replace sample IDs for a production release. Check ad placement against Google's policies.
- Enable leaked-password protection in Supabase Auth security settings if available, or document plan limitations; audit SECURITY DEFINER RPCs and rerun security advisors.
- Publish a monitored developer support/privacy email and accurate final identity, analytics, SMTP and retention disclosures; complete required Google Play policy declarations and screenshots.
- Configure a stable private upload signing key (never share it in chat). Manually trigger the signed Play bundle workflow and inspect its signature before uploading. A successful debug APK does not prove a Play AAB is ready.

## Points retirement and Play Console rejection (13 October 2026)

Version 0.3.6 (10) removes MoneyMate Points from website and Android candidate sources. Historical records are retained. Apply the revoke-only migration only after new clients have rolled out. Removing points does not guarantee a Personal Google Play developer account is eligible under Google's organization-account financial services requirements. Confirm the correct classification before resubmitting. The previously rejected version 0.3.5 (9) bundle is unchanged; code 10 requires a new signed AAB.

## Direct Google sign-in candidate (0.3.7 / version code 11)

This candidate adds an official Google sign-in button on the website and Android Credential Manager sign-in; both use the public Google OAuth Web Client ID and have Supabase validate the Google ID token. Existing browser PKCE OAuth remains accessible as a fallback. This is not a custom domain and does not edit any saved account or financial data.

**Hold before production:** Configure the Google Cloud **Android OAuth client** with `nz.moneymate.app` and the **Google Play App Signing key SHA-1** from Play Console → App integrity. For local test APKs, also register the debug certificate SHA-1 separately. Configure Web OAuth authorized JavaScript origins (`https://frymastercheese.github.io`). Test an existing Google account on real Play-signed Android and on the website and verify the same Supabase user ID and unchanged existing financial records. Keep the browser backup while testing. Google Play will require a new signed bundle with code 11 if and when approved. This change does not resolve the separate Play Console organization-account policy issue.
