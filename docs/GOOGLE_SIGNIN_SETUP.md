# Google sign-in setup (not yet activated)

MoneyMate NZ uses Supabase project efdegdlsrkxlnjgbxtep for identity and private account cloud records. The user's verified Supabase ID, not a typed email address, owns those records.

1. Create a Google OAuth consent configuration and Web OAuth client in https://console.cloud.google.com/apis/credentials .
2. Put this exact authorized redirect URL in Google: https://efdegdlsrkxlnjgbxtep.supabase.co/auth/v1/callback .
3. Enable Google in Supabase > Authentication > Providers > Google. Enter Google client ID and secret directly there, not in GitHub or this chat.
4. Allow https://frymastercheese.github.io/MoneyMate-NZ/ under Supabase Authentication > URL Configuration redirects.
5. Test real Google login, account recovery, identity linking and deletion before changing googleEnabled to true in root and docs account-config.json.
6. The native Android Google flow additionally requires Android OAuth credentials and signing certificate SHA fingerprints. Browser OAuth does not enable native Android sign-in.

Supabase can link verified-email identities under its own security rules. Do not automatically merge unrelated accounts or financial documents just because email strings match. Documentation: https://supabase.com/docs/guides/auth/auth-identity-linking .

Production email recovery requires a configured SMTP provider and testing. Google-only account deletion requires a separate secure reauthentication flow. No secret or service-role key belongs in client files.

The owner-only cloud snapshot uses compare-and-swap revisions to reject stale writes; conflicts preserve local copies until deliberately resolved. Users must opt into financial data upload.
