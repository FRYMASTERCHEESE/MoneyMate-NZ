# MoneyMate NZ · Open banking production access checklist

**Status: NOT LIVE.** Research checkpoint: 13 October 2026. This document is developer preparation, not provider approval or customer-facing functionality. Do **not** publish a working Connect button until the mandatory gates below pass. The current MoneyMate NZ website and Android app continue to use optional MoneyMate account/cloud sync, **not automatic bank imports**.

## New Zealand — Akahu

- **Production access:** Personal Apps are free but limited to the owner's own Akahu account and are not suitable for multiple MoneyMate NZ users. Request a **full app** and API credentials from Akahu: https://developers.akahu.nz/me/docs/getting-started . Contact: developers@akahu.nz . Application accreditation is separately required before public use: https://developers.akahu.nz/docs/app-accreditation .
- **Developer and expected fees:** https://www.akahu.nz/pricing . Check current commercial pricing and required risk/accreditation evidence with Akahu; nothing has been purchased or agreed to.
- **OAuth:** Akahu issues an App ID Token and App Secret to the developer. OAuth authorisation must occur at the provider/bank, with redirect allow-list, unpredictable expiring per-user `state`, and *server-side* exchange of the short-lived `code` for a token. The App Secret and resulting user token are server-only: https://developers.akahu.nz/docs/authorizing-with-oauth2 .
- **Bank-app redirect testing:** Android must use an external browser / proper app redirects (not a trapped iframe or embedded WebView); test supported bank flows before opening to customers: https://developers.akahu.nz/docs/official-open-banking-oauth .
- **Data:** Request the minimum account and `TRANSACTIONS` read scopes, no payment permissions. Use authenticated provider requests, pagination and webhooks with verified RSA signatures, plus backstop polling. Handle modified/deleted transaction IDs and migrated account IDs: https://developers.akahu.nz/docs/reference-webhooks .

## Australia — Basiq

- **Developer onboarding:** Register the app in the Basiq dashboard, choose the app's consent policy/institutions, generate a **server-only** API key, then contact Basiq to arrange an allowed **CDR access model**. Dashboard and API key do not, by themselves, enable public CDR Open Banking: https://api.basiq.io/docs/getting-started .
- **Access arrangement:** Basiq describes principal/representative and other access models: https://api.basiq.io/docs/supported-access-models . Confirm the appropriate commercial/regulated agreement and exact data use/retention. Basiq's ACCC accreditation record: https://www.cdr.gov.au/find-a-provider?provider=ADRBNK000208 .
- **Consent:** Backend creates a provider user and issues a user-bound `CLIENT_ACCESS` token. The consumer uses Basiq's hosted Consent UI. Do not mistake public page links for completed connections: https://api.basiq.io/docs/quickstart-api and https://api.basiq.io/docs/consent .
- **Data:** Request posted transactions only as scoped by active user consent. Track consent expiry, reconnect, data correction/deletion, CDR dashboard disclosure and deletion obligations. Do not mix AUD into NZD reports or automatically convert with an implied exchange rate.

## Implemented on this feature branch (safe foundation only)

- `supabase/functions/_shared/bank-import.mjs` validates *server-obtained* Akahu and Basiq posted transaction payloads, insists on their country currency, converts to signed integer cents, bounds description/identifiers, avoids raw account balances and private bank metadata, rejects pending data, and stops on conflicting duplicate identifiers.
- `supabase/migrations/20261013_moneymate_bank_import_staging.sql` defines a **review-only** per-user import ledger and consent metadata schema, with account-owner row-level read policies, no direct client writes, and ownership constraints. It intentionally stores **no OAuth tokens, API keys or bank passwords**. The migration is **not applied** simply by merging a PR.
- Unit tests test these safety rules using synthetic, non-customer data. They do not make real bank connections or prove complete isolation under a deployed Supabase RLS session.

## Mandatory production release gates (all outstanding)

1. Owner signs contracts / provider approval for full multi-user Akahu and appropriate Basiq CDR arrangement; verify exact supported banks and permitted purposes; estimate pricing and choose whether user subscriptions cover ongoing costs.
2. Complete security assessment and final privacy policy: explain third-party financial-data sharing, cross-border storage, retention, access, revocation, and account deletion. Add breach response and user support contacts. No stored bank passwords, no client-accessible secrets, no unencrypted server-side tokens.
3. Deploy a secure backend OAuth consent-start/callback protocol for Akahu (including CSRF state/session binding and redirect validation), and Basiq user-specific Consent UI tokens. Store credential material *only in encrypted server-side vault*, with key rotation and scoped access; implement durable consent lifecycle + disconnect/revoke.
4. Add server-only scheduled/manual incremental import with authenticated provider API access, pagination, retries, rate limiting, signed webhooks where supported, safe idempotent upserts and corrections/deletions. Never grant a browser or mobile client direct write to the staging ledger.
5. Do not count bank-import ledger items in budgets until user-approved deduplication with the existing cloud/Android manual entries is tested. Preserve the existing local records and handle offline edits. Guard currency separation, posted vs pending status and per-account ownership.
6. Validate RLS with distinct real test accounts (same user across Android/website, and separate users), including direct REST insert/update denial, logout, revoked consent, deleted accounts, interrupted OAuth, offline conflicts and duplicate/mutated transaction fixtures.
7. Final provider-required staging/production approvals and Android app-redirect checks; only then expose Connect controls after server readiness checks. Roll out to selected test users before public access.

**Security:** Do not put provider keys into `web-app.js`, GitHub Pages, the APK, GitHub Actions logs or public issues. Do not ask end users to paste bank usernames or passwords. Never award MoneyMate Points for higher spending or for connecting a bank. App daily check-in points remain separate.
