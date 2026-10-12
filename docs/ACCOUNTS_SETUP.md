# MoneyMate NZ account deployment

## Current status

The requested dedicated project is **MoneyMate NZ**, in organization **FryMasterCheese** (`fyijnywzkacqebffqtvt`), region **Sydney (`ap-southeast-2`)**. The organization reports Free plan. The connected MCP project-cost tool returned UNAVAILABLE: no project was created, no price was confirmed. Check the dashboard's project quote and free-project quota before creation; do not assume a free slot. Existing Bebo and inactive projects are untouched. Store the database password in a password manager.

The native Java client uses Supabase GoTrue HTTPS, no extra SDK dependency. Accounts are optional; blank client configuration keeps local budgeting usable and displays an unavailable message. No financial records are uploaded, no public tables or buckets or migrations are needed. Keep unused Data API disabled. Future exposed tables require explicit grants and owner RLS including UPDATE WITH CHECK.

## Exact configuration

1. Create the dedicated project after accepting its dashboard quote. Auth → Providers → Email: enable email/password sign-up, **Confirm email ON**, minimum password length **12**. Disable anonymous sign-ins. Configure rate limits. CAPTCHA is not implemented in this client; do not enable it without adding support.
2. Configure **custom SMTP** with a verified sender/domain. Default SMTP is restricted and unsuitable for app users. New Free projects need custom SMTP to customize templates. SMTP secrets stay in Supabase. Add the chosen provider's name/privacy URL/retention to both privacy documents.
3. Set Site URL to `https://frymastercheese.github.io/MoneyMate-NZ/`. No deep link or wildcard redirect is needed: the app and website accept emailed codes.
4. Confirm signup email subject: `Confirm your MoneyMate NZ email`. Body: `<h2>MoneyMate NZ</h2><p>Your confirmation code is {{ .Token }}.</p><p>Enter it in Account → Confirm email, then sign in. Ignore this email if you did not register.</p>`.
5. Reset password subject: `Reset your MoneyMate NZ password`. Body: `<h2>MoneyMate NZ</h2><p>Your password reset code is {{ .Token }}.</p><p>Enter it in Account → Reset password with email code, or the MoneyMate NZ account deletion page. Ignore this email if you did not request it.</p>`. Recommended OTP expiry **600 seconds**, JWT expiry **900 seconds**. Disable SMTP link tracking.
6. Deploy `supabase/functions/delete-account` with gateway JWT verification disabled as in `supabase/config.toml`. The handler independently calls Auth `/user`, checks confirmed email, verifies password, matches identities, revokes global refresh sessions, and hard-deletes only that ID. Supabase supplies `SUPABASE_URL` and `SUPABASE_SERVICE_ROLE_KEY` in the Edge Function environment only. Never put admin credentials in Android, web configuration, Actions variables, commits or logs. Allowed web origin: `https://frymastercheese.github.io`.
7. GitHub repository Settings → Secrets and variables → Actions → **Variables**:
   - `MONEYMATE_SUPABASE_URL` = `https://<project-ref>.supabase.co` without trailing slash.
   - `MONEYMATE_SUPABASE_PUBLISHABLE_KEY` = the project's `sb_publishable_…` key.
   These are public client values. Builds deliberately reject legacy anon JWTs and secret/service-role keys. Set the same environment variables for local builds.
8. Put those same public values into `docs/account-config.json` as `url` and `publishableKey`. Deploy Pages and confirm privacy/deletion pages are reachable and deletion is enabled. The existing workflow publishes main, so draft branch files are not automatically live.
9. Keep **MONEYMATE_TEST_KEYSTORE** unchanged. Trigger Android CI after setting public variables. Version **0.3.0 (4)**; app ID `nz.moneymate.app`; SQLite schema version 2 unchanged. Release AAB remains unsigned.

For CLI deployment, discover the current commands with `supabase --help`, `supabase login --help`, `supabase link --help`, and `supabase functions deploy --help`; authenticate, link this checkout to the dedicated project, and deploy `delete-account` with current flags. No schema migration is needed. Never commit access tokens/database passwords. Run security advisors on the live project before release.

## Data behavior

Guest uses original `moneymate.db`; each canonical Auth UUID gets its own database. Typed email and user metadata never determine ownership. First sign-in opens an empty profile. Explicit import copies all five tables only into an empty target; atomic restore leaves the original intact. Backups restore into the current profile after confirmation. Sign-out clears encrypted tokens even offline, retaining hidden account records. Offline display uses the last signed-in profile; accounts are app-level separation, not financial database encryption or device security.

Deletion waits for backend success before local removal. Failure preserves records/session. If the app is killed between server deletion and local cleanup, clear storage manually. Web/other-device deletion cannot erase offline databases or exported backups. Remove them separately. Reset-code sessions are temporary and never activate a local profile. Refresh tokens rotate before privileged requests.

## Verification required before release

Run `node --test supabase/functions/delete-account/handler.test.mjs`, Android CI unit/build tests, and emulator `AccountTest`/`VisualTest`. Android checks include real v1 migration, profile separation, atomic restore, Keystore persistence, offline sign-out and recovery-session separation. Mock tests do not prove live Auth or SMTP.

With two disposable real email accounts, verify unconfirmed sign-in denied, confirmation and sign-in, restart, password reset/old password denied, invalid/expired/reused codes, rate limits, offline records and sign-out, A/B/guest isolation, import every table, backup/restore, failed/successful in-app deletion, web deletion without app, and deleted-user login denied. Remove test accounts. Confirm no sensitive logging.

Supply a monitored support/privacy email and final provider/retention details. Play Data safety must disclose email/user ID and Auth/security processing plus AdMob. Set hosted privacy URL and deletion URL **`https://frymastercheese.github.io/MoneyMate-NZ/delete-account.html`**. Verify both deletion flows with production configuration, replace test ads and sign the AAB. This draft is not a claim of Play approval.

Sources checked 12 October 2026: [password auth](https://supabase.com/docs/guides/auth/passwords), [email templates](https://supabase.com/docs/guides/auth/auth-email-templates), [SMTP](https://supabase.com/docs/guides/auth/auth-smtp), [GoTrue API](https://github.com/supabase/auth/blob/master/openapi.yaml), [changelog](https://supabase.com/changelog), [Google Play deletion requirements](https://support.google.com/googleplay/android-developer/answer/13327111?hl=en-EN).
