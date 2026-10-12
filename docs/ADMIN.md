# MoneyMate NZ — Admin dashboard

This is a read-only management dashboard at the MoneyMate NZ website, accessible through Account → Admin dashboard after signing in with an account that holds the server-side administrator role. The admin URL fragment can also be used after signing in.

## Authorisation

- The moneymate_admins table links admin membership to a verified Supabase user UUID. An email address visible in a browser is not permission.
- Row-level security is enabled on the admin table. Browser clients cannot list, add, alter or remove admin memberships.
- The moneymate_admin_overview database function checks the authenticated user ID and role before reading aggregate statistics. It uses SECURITY DEFINER to count accounts. Anonymous clients have no permission to call it.
- Signed-in users without an admin membership are denied. Hiding the admin menu is a usability feature, not the security boundary.
- Never place user email addresses, administrative API keys or a service-role secret into the public JavaScript or configuration.
- No individual transactions, budgets, account emails or passwords are returned.

## Operations

The panel displays total and confirmed registrations, Google account count, new registrations over seven days, cloud-backed account count and latest registration time. It links to Supabase, GitHub Actions and Google Play Console for owner operations. It cannot delete customers or inspect financial records.

The migration file is supabase/migrations/20261012_moneymate_admin_overview.sql. Provision memberships separately, only after checking the intended account has a verified email and matching identity. Never grant access solely because the client entered an email address.

Sessions in the current website are held in browser memory; after a full reload the owner signs in again before reopening the panel. Real-account admin sign-in and unauthorized-account denial should still be tested on separate mobile and desktop browsers.
