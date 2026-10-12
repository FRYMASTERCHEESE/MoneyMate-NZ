# MoneyMate NZ: unified accounts and device syncing — release checklist

**Status:** staged implementation. Do **not** advertise Google sign-in, guaranteed account recovery or automatic cross-device backup as live until an end-to-end test with two separate accounts and devices has passed.

## 1. Account data ownership

Supabase project: `efdegdlsrkxlnjgbxtep` (Sydney). Browser and Android use the same Supabase Auth user ID. Never use an email address as a financial table owner key. The server snapshot RPC `read_account_snapshot` reads only the authenticated owner's record; `commit_account_snapshot` enforces expected-revision compare-and-swap and rejects writes from stale revisions.

Users must explicitly enable cloud syncing to upload financial records. Local guests are not synced. Android and website preserve existing records when they encounter divergent histories instead of silently merging or deleting entries. Users should export a full JSON backup before enabling sync or resolving a conflict. A successful cloud sync is not a substitute for a separate recoverable backup.

## 2. Google sign-in prerequisite (Google account owner action)

The Google OAuth client is **not configured or verified**. Until it is, keep `googleEnabled: false` in `docs/account-config.json` and in the root copy; the Android build consumes the same public flag.

1. Open [Google Auth Platform](https://console.cloud.google.com/auth/overview) and set up an authorized Google OAuth consent screen for **MoneyMate NZ**. Only request basic `openid`, `email`, `profile` scopes unless other scopes are justified.
2. Create a **Web application** Google OAuth client. Add the authorized redirect URI `https://efdegdlsrkxlnjgbxtep.supabase.co/auth/v1/callback`. This is Google's callback into Supabase, *not* the MoneyMate frontend URL.
3. Open [Supabase Auth Providers — MoneyMate NZ](https://supabase.com/dashboard/project/efdegdlsrkxlnjgbxtep/auth/providers) and enable **Google**, entering the Google OAuth client ID and client secret **directly into Supabase**. Never place the secret in GitHub, the Android app or a chat.
4. Under [Supabase Authentication URL Configuration](https://supabase.com/dashboard/project/efdegdlsrkxlnjgbxtep/auth/url-configuration), allow the exact website redirect `https://frymastercheese.github.io/MoneyMate-NZ/` and native Android callback `moneymatenz://auth/callback`. Validate the allowed redirect patterns and the Google project's production status.
5. Only after testing signup and login on the website **and** Android with the same Supabase user ID, change `googleEnabled` to `true` in both public config copies, rebuild the Android APK/AAB, and test again before publication.

MoneyMate uses OAuth PKCE (no Google secret in the app). Supabase automatically links OAuth identities when emails are verified and match; never force-merge two different user IDs using email alone. For different email addresses, use Supabase's *manual identity linking* feature with an authenticated existing session after enabling that option, and verify the resulting user ID before syncing records. An unlinked second Google identity has a separate profile.

## 3. Email passwords and recovery

The UI and Android validation allow passwords of **8–128 characters**. Supabase server-side password policy must also allow this minimum; check [Authentication configuration](https://supabase.com/dashboard/project/efdegdlsrkxlnjgbxtep/auth/providers), email confirmation templates and production SMTP delivery. Eight easy/guessable letters are weaker than longer random passwords; recommend a unique password or Google login. Password recovery must be tested end-to-end with a real incoming email.

## 4. Cloud rollout validation

Before enabling on a live account, back up the original guest and account profiles. Use separate Android and laptop devices:

- Sign up by email, confirm a message, sign in, enable cloud sync and save a transaction; verify it appears on another device after authentication and consent.
- Repeat from laptop to Android, and after fully closing and restarting both.
- Edit on two offline devices, reconnect, and verify neither side silently overwrites the other. Reconcile by explicitly exporting both backups first.
- Check sign-out and a different account cannot read the first account's records. Test revoked sessions, expired tokens, retries, Google OAuth callback cancellation, and a device reinstall.
- Confirm cloud sync opt-in and updated privacy disclosures; never upload financial records for guests or unsigned users.
- Verify account deletion and data retention terms; no irreversible deletion should occur without explicit confirmation.

No Google Cloud account, paid email provider or Play release has been created or paid for by these code changes.
