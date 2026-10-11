# Publishing MoneyMate NZ on Google Play

**Play Console**: https://play.google.com/console/

Google Play Store is a store operated by Google, not a website account GitHub can create. The developer must register and verify their own Play Console developer account, pay Google's registration fee where applicable, and submit the app for Google's review.

## Developer setup

1. Visit the Play Console and create/verify a developer account.
2. Create an app named **MoneyMate NZ: Budget Tracker**. Default language: English (New Zealand) if offered. App: Free; includes ads.
3. The unique Android application ID in this project is `nz.moneymate.app`. Confirm it is available and keep it consistent.
4. Use [store listing draft](STORE_LISTING.md), [website](https://frymastercheese.github.io/MoneyMate-NZ/) and [privacy policy](https://frymastercheese.github.io/MoneyMate-NZ/privacy.html), **after checking that all pages are publicly available**.
5. Add an accurate support email and update the policy and store listing before production.
6. Configure the real AdMob app ID, ad unit ID, consent options and Google Play declarations; the code currently uses **test ad IDs**.
7. Prepare real-device screenshots, a unique 512px icon, and a 1024x500 feature graphic.
8. Arrange an Android App Bundle signed with a **privately held upload key** under Google Play App Signing; do NOT check in the keystore or its passwords.
9. Perform a full functionality, accessibility and privacy audit on actual Android devices.
10. Finish mandatory closed testing for your account if Google requires it, then submit for review. Google controls approval.

## Updating easily

- Website landing page: `docs/index.html`
- Public privacy page: `docs/privacy.html`
- FAQ/help: `docs/help.html`
- App code: `app/src/main/java/nz/moneymate/app/`
- Builds and test artifacts: https://github.com/FRYMASTERCHEESE/MoneyMate-NZ/actions
- App store listing draft: `docs/STORE_LISTING.md`

The automated CI currently generates a **debug APK** and **unsigned release AAB**, neither of which replaces proper Play release signing or Play Console approval.
