# Release candidate update — 24 October 2026

The visual refresh and planning/AdMob changes are now candidate 0.4.0 (15). Play Console inspection confirmed that 14 is already used by 0.3.10; 15 is the next unused code. See `PLAY-RELEASE-0.4.0.md` for the new scope and release checklist. The evidence below records the prior inspected revision; new changes need their own CI run. Local web tests after the refresh: 93 passed.

# MoneyMate NZ release verification — 24 October 2026

## Inspected revisions and live state

- Main: `1ca691449c893185341216332848ec405f766ba8`.
- Draft PR #30 starting head: `6c1fb36f030c85052ed7188b3a80d57e967668b9`; open and unmerged when inspected.
- Public GitHub Pages root returned HTTP 200. Its live service worker identifies `moneymate-nz-web-v11-points-retired`, while the starting draft uses v13. Draft changes are not verified as deployed.
- Live browser home renders with empty local records. This does not verify authenticated or mobile behavior.
- Existing head has successful GitHub checks for web tests and Android API 35/36 verification and optimized cold starts. The recorded API 35 cold-start retry must not be represented as a first-attempt pass.

## Improvements in this continuation

- Ordinary Android and web sign-out uses Supabase's `local` scope so another phone/browser is not unexpectedly signed out. Password reset keeps global session revocation.
- A refresh response identifying a different account now fails authentication instead of entering the offline fallback. The mismatched credentials are not persisted; financial records are retained.
- Browser regressions reproduce both defects before the fix, and cover offline sign-out. Android instrumentation covers session scope and refresh identity mismatch.
- Cache v14 makes the web changes eligible for refresh after a future reviewed deployment; it does not deploy them.

## Data and synchronization evidence

- Both clients use the same Supabase project and revision-checked whole-account snapshot endpoints, covering transactions, budgets, goals, bills, and recurring records.
- Local JavaScript tests exercise persistence, token rotation, account separation, simulated two-client sync, edits/deletions, concurrent changes, rollback/recovery, amount/date validation and duplicate-safe imports. Synthetic transport tests are not production round trips.
- Sync is opt-in. Conflicting edits stop and preserve both copies; automatic merging is not implemented. Users need a backup before deliberately choosing the cloud copy.
- ANZ tests cover NZD reconciliation, yearless leap dates, invalid-date rejection and repeat import. Browser PDF support needs readable text; real scanned-PDF/OCR behavior still needs device testing.

## Read-only Supabase inspection

- Project `efdegdlsrkxlnjgbxtep` was ACTIVE_HEALTHY. All ten public tables have RLS enabled.
- Financial tables have owner predicates and UPDATE ownership checks. Snapshot SELECT is restricted to `auth.uid()`; snapshot writes pass through a revision-checked function using the authenticated caller's ID, schema validation and a fixed empty search path.
- Privileged RPC advisor warnings require context: snapshot commit verifies the caller/account; admin overview checks UUID membership. Historical points functions remain callable even though repository revocation SQL exists and is explicitly marked for after client rollout. Do not apply it blindly before deployment inventory.
- No-policy findings on the admin/points tables are deny-by-default, not evidence of unrestricted access.
- Leaked-password protection is disabled. Review availability/cost and enable through a separate authorized production configuration change. See [password security](https://supabase.com/docs/guides/auth/password-security#password-strength-and-leaked-password-protection).
- A missing goals ownership index is an informational performance finding. No index was removed because it was reported unused. See [foreign-key index guidance](https://supabase.com/docs/guides/database/database-linter?lint=0001_unindexed_foreign_keys).
- Last-24-hour logs inventory contained pooler/Postgres entries, without auth/edge entries available for login diagnosis. No user financial rows were read or changed.

## Required before a production release

1. Run the new Android checks at the final commit; do not transfer earlier CI success to new code.
2. Verify real Google and email login, confirmation/recovery delivery, token refresh and process reopening on physical Android devices and browsers. Confirm provider redirect configuration and the production signing certificate.
3. With a dedicated test account, test Android-to-web, web-to-Android and Android-to-Android round trips of every collection, updates and deletions; reconnect after offline edits and resolve conflicts without loss or duplication. Check that signing out of one device preserves the others.
4. Test actual ANZ text and scanned PDFs, multi-page statements, repeated/overlapping imports and balances against a manually checked fixture. Do not put personal statements in GitHub.
5. Test narrow mobile viewports, keyboard/forms, navigation, import previews, export/restore and offline reopening on real mobile browsers. Desktop rendering is not a mobile acceptance test.
6. Review production security settings and RPC grants; run isolated two-user RLS negative tests without touching customer records. Verify account deletion and data retention in staging.
7. Verify the signed `nz.moneymate.app` production bundle, Play configuration, privacy/data-safety declarations and device smoke tests. Merge/deploy only after review, then compare served assets and installed version to the approved commit.

No production schema, OAuth configuration, accounts, financial records, bank connections, billing, signing credentials or live release were changed by this continuation. Automatic bank connections remain unavailable.
