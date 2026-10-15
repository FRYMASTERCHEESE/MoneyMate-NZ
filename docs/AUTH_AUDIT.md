# Google sign-in and session reliability (free Supabase setup)

Keep the existing Supabase project URL, free plan, public client configuration, Auth users, financial data, package ID, signing keys and testers. Do not purchase domains, upgrade plans, enable billing or add paid services.

The existing website root and Android moneymatenz://auth/callback return URLs are the intended callbacks. Retain them in Supabase's redirect allowlist and retain the existing project's /auth/v1/callback URI in the existing Google web OAuth client. Google and email login share the canonical Supabase user ID and session vault, not email-derived database names.

This change requests Google account selection on web and Android; handles browser OAuth cancellation/denial; restores the previous saved login after unsuccessful callbacks; and prevents temporary browser-storage read failures from deleting the saved login. Existing encrypted Android and IndexedDB website session storage remain in place.

Google chooser branding is NOT fixed or verified by this code. Free Google Auth Platform branding/verification may provide an app name/logo where Google supports it. Keep the Supabase hostname; a custom auth domain is outside the approved scope. Do not claim that changing a button label or Supabase project name changes Google's chooser.

Validation: 84 JavaScript tests pass locally. Session restoration tests exercise saved login, expired-token rotation, offline restoration, transient vault errors, revocation, identity mismatch and concurrent refresh in one page. OAuth tests exercise PKCE, account selection, success, cancellation, missing verifier and failed exchange. Existing profile isolation and sync conflict tests also pass.

An Android instrumentation test now checks Google PKCE login, encrypted-vault reopening, expired refresh-token rotation and reopening again with the rotated token. It uses synthetic transport responses; Android CI must pass before treating this test as verified.

Release gate: real email-password login, Google chooser, Android process restart/deep-link delivery, account switching, logout, offline reconnect and two-device cloud sync still need end-to-end verification with synthetic records. Global logout currently signs other devices out. No release, production deployment or data migration is part of this PR.

Reference: https://supabase.com/docs/guides/auth/social-login/auth-google
