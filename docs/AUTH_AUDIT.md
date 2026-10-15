# Google sign-in branding and verification

This change requests Google account selection on web and Android, handles browser OAuth cancellation/denial, and restores the previous saved login after an unsuccessful callback. It preserves existing project configuration, PKCE, session storage, application ID and signing setup.

Google's account chooser branding has NOT been verified or fixed by this code change. The app name/logo must be configured and, where required, verified in the existing Google Auth Platform project. To replace the Supabase callback hostname, Supabase supports a custom domain on a paid plan with a paid add-on. An owner-controlled domain and DNS verification are required. Do not invent a domain or replace the Supabase project.

Before activating a custom domain, add its /auth/v1/callback URL to the existing Google web OAuth client alongside the original Supabase callback. Preserve client IDs, secrets, audience and testers. Supabase documents that the original project domain continues working after custom-domain activation, so existing clients do not inherently need an Android release for this dashboard change.

Validate the actual Google chooser after changing branding/domain settings. A successful sign-in that skips the chooser does not verify the displayed name. Preserve Auth UUIDs; do not merge users based only on an email address.

Validation: 75 JavaScript regression tests pass, including PKCE account selection, callback success, query/fragment cancellation, missing verifier, exchange failure, persistent vault storage, profile isolation and cloud conflict tests. These are automated tests with mocked provider responses, not proof of live Android or Google behavior.

Before release: run Android CI and real-device tests for Google/email login, restart, token refresh, callback delivery, switching accounts, logout, offline reconnect, two-device sync and conflict preservation using synthetic records. Existing logout uses global scope and may sign other devices out. No Android release is included in this change.

References:
- https://supabase.com/docs/guides/auth/social-login/auth-google
- https://supabase.com/docs/guides/platform/custom-domains
