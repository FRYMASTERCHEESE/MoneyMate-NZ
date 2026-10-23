# MoneyMate NZ 0.4.0 release candidate

Prepared 24 October 2026. Package `nz.moneymate.app`, candidate version code **14**. This is a draft release, not an uploaded or approved Play release. Confirm 14 exceeds every version already used in Play before building the signed bundle.

## Ready for review

- Shared vector wallet/M logo, adaptive Android launcher and themed monochrome icon, 512×512 opaque Play icon, 1024×500 feature graphic.
- Emerald/citrus design; five main navigation choices on phones, with other sections under More. Account remains directly accessible in the web header.
- Monthly cash flow explicitly means recorded income minus expenses. Category budget remainder, unpaid bills this month and completed goals have their own summaries.
- Repeating entries catch up when returning to the foreground and while open. This records bookkeeping entries only, never bank payments. Writes use existing atomic/profile-protected paths; excessive Android catch-up now rolls back completely, matching web behavior.
- Consent-gated anchored adaptive AdMob banners, separate labelled placement, empty-slot hiding, lifecycle cleanup, and privacy choices when required by UMP. Account, export, help, banking and admin pages do not display banners. Tests always use Google's test publisher; production rejects all Google sample publisher IDs.
- Existing sign-in, sync conflict protection, ANZ preview/import, local backups and account separation retained. No financial schema migration.

## Store listing draft

**Name:** MoneyMate NZ

**Support email:** moneymatenzapp@gmail.com (owner-supplied public address; mailbox delivery/monitoring still needs verification)

**Short description:** Plan your NZ dollars. Track spending, budgets, bills and savings goals.

**Full description:**

Make room for what matters with MoneyMate NZ, an everyday money planner built around New Zealand dollars.

See recorded income and expenses in one place. Set monthly category budgets, track savings goals and keep upcoming bills in view. Repeating income and expense entries catch up when you open or return to MoneyMate.

Use a local profile offline, or sign in and enable account syncing to share records with the MoneyMate NZ website and your other devices. Conflicting offline changes need your review before replacing a copy.

Import supported NZD CSV or PDF statements with a preview before saving. PDF compatibility depends on the statement layout and text quality. Review imported amounts and dates. Export CSV transactions or save a complete JSON backup whenever you need one.

MoneyMate is a budgeting tool. It does not connect to your bank, move money, pay bills or provide financial advice. Cash flow is based on your recorded entries, not your bank balance. Android contains advertising.

**Release notes:**

New MoneyMate logo and refreshed design. Simpler phone navigation, clearer monthly planning, and automatic catch-up for repeating entries. Improved sign-in persistence, statement import checks and adaptive ad placement.

## Production gates

1. **Closed test:** supplied Play screenshot reports 12 opted-in testers for 7 continuous days. Google's requirement is at least 12 continuously opted in for 14 days before applying. Keep testing and collect actual feedback; do not invent engagement or application answers. [Official testing requirement](https://support.google.com/googleplay/android-developer/answer/14151465?hl=en).
2. **Current build:** all Android API 35/36 tests, release lint, optimised builds and cold-start runs must pass for the candidate. Do physical-device/real-account acceptance checks in `RELEASE-READINESS.md`; emulator checks do not establish Google/email delivery, real two-device sync or real ANZ statement accuracy.
3. **Signing:** all six required production signing/AdMob secret names exist in GitHub (read-only inventory). Values were not read or changed and their correctness is unverified. After reviewed merge, use the existing manual **MoneyMate NZ signed Play bundle** workflow on main. It verifies the upload signature and outputs the AAB and checksum; it never uploads to Play automatically. Confirm the upload certificate matches Play App Signing and version code is unused. Test upgrading from the current closed-test build without data loss.
4. **AdMob:** verify the actual app and banner units are approved, linked to the correct Play app and serving. Configure and publish the applicable consent messages in AdMob Privacy & messaging. Exercise consent acceptance, refusal, changed choices, no-fill, offline and resume on test devices. Never click live ads during testing. Adaptive sizing improves fit; it does not guarantee earnings. [Banner guidance](https://developers.google.com/admob/android/banner), [UMP guidance](https://developers.google.com/admob/android/privacy).
5. **app-ads.txt:** verify the AdMob publisher ID and the developer website listed in Play. The file must be reachable at the website host root, which for `frymastercheese.github.io` is `/app-ads.txt`, not `/MoneyMate-NZ/app-ads.txt`. The host-root file was checked successfully (HTTP 200) on 24 October 2026 and lists `pub-4051392846058327` as a DIRECT Google seller. Confirm this matches the actual AdMob account and Play developer URL; AdMob dashboard verification remains unverified. No seller record was changed. [Official setup](https://support.google.com/admob/answer/9363762?hl=en).
6. **App content:** review privacy policy, public deletion URL, content rating, target audience, ads declaration, financial features declaration and Data safety against the final signed app and every included SDK. Optional Supabase account/sync sends email, account identifiers and selected financial records off device; AdMob also processes advertising/device/network data. Do not declare "no data collected". Do not claim end-to-end encryption. [Data safety guidance](https://support.google.com/googleplay/android-developer/answer/10787469?hl=en), [Mobile Ads SDK disclosure guidance](https://developers.google.com/admob/android/privacy/play-data-disclosure).
7. **Screenshots:** old `store-assets/phone-*.png` and `capture-provenance.json` still identify the previous version until regenerated from this candidate's successful emulator capture. Fresh screenshots must display example data and must match the version submitted. New icon/feature graphic are ready for visual review. The icon uses 32-bit RGBA PNG with opaque alpha; the feature graphic uses 24-bit RGB PNG. `tests/play-assets.test.mjs` verifies these Play format requirements.
8. **Deployment:** keep draft changes separate from live main. After review and merge, compare served website files with the approved commit, then verify the installed signed version. Current draft test success is not a live deployment claim.

## Cost boundary

No paid service, subscription, bank connector, advertising campaign or paid security upgrade was enabled. Recurrence runs on the device/browser and CI uses the existing repository workflows. Do not upgrade Supabase or add chargeable providers to unblock release without a separate budget decision. The free database plan still has service limits; 100% uptime or readiness is not promised.
