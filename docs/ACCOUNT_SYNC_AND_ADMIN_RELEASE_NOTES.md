# MoneyMate NZ — safe signed-in sync and administrator UI candidate

## Why only one cloud-backed profile showed in the dashboard

Account registration is separate from consent to upload private budgeting data.
The existing app and website only save to Supabase after the signed-in account
has enabled optional secure cloud sync. A guest profile is always local-only.

## What this candidate improves

- The Android app now requests **server-verified** admin statistics over
  `moneymate_admin_overview` using the signed-in user's own access token.
  Only a backend-authorized `moneymate_admins.user_id` may see the
  **Admin dashboard** tab. It does not trust displayed email text, and
  denies/hidden RPC access on failure. The backend currently authorizes
  exactly one account, belonging to coreyedge123@gmail.com (verified in a
  read-only query on 15 October 2026).
- If an account **already explicitly enabled** cloud saving on one device,
  a second device/browser with an **empty signed-in profile** automatically
  attaches to that account's existing cloud copy and downloads it safely.
- If a device has any financial records, its local data is never silently
  replaced. Users must keep backups and explicitly resolve conflicts.
  Revision checking and server-side per-account isolation remain in place.
- Existing optional cloud sync continues to run after sign-in and record
  changes, including retries when online.

## Privacy and remaining prerequisite

This candidate **does not silently upload** local personal financial records
from an account that has never opted in. For first-time cloud activation,
users still explicitly choose **Enable secure cloud sync** on their first
device. A live public release that defaults to uploading financial records
without prior notice/consent would need updated sign-in disclosure, privacy
policy and Google Play Data safety review. Do not misrepresent registration
count as cloud-synced profiles.

## Tests and release protection

- Tests cover server-granted versus email-only Android admin access,
  and new/empty versus occupied cloud profile behavior.
- Review the Android CI optimized instrumentation tests and browser sync tests
  before merging. Test real Google/email accounts, restore/upgrade and
  two-device offline conflict handling on non-sensitive financial fixtures.
- On merge, GitHub Pages redeploys; do not merge without verifying the website
  against existing PR #23 and authentication PRs #21/#22.
- No Supabase DDL, customer data, signing material, Play testing groups,
  package name, app version or deployment is changed by this candidate.
- Keep the existing signed Play 0.3.7 (11) closed-testing release active and
  do not submit a new bundle without separate release approval.
