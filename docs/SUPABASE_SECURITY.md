# Supabase security follow-up — 14 October 2026

## Read-only live findings

Project: MoneyMate-NZ (`efdegdlsrkxlnjgbxtep`), Sydney, PostgreSQL 17.11.
PR #23 started at `132e2054610eee4c0f905f1b01faf1efd2c53242`.
No live SQL mutations, account records, password hashes, tokens or secrets were used.

The security advisor confirms leaked-password protection is disabled. The owning
organization is on Free. [Supabase documentation](https://supabase.com/docs/guides/auth/password-security)
requires Pro or above for this feature. The owner must decide whether to upgrade,
then enable protection in [Email Auth settings](https://supabase.com/dashboard/project/efdegdlsrkxlnjgbxtep/auth/providers?provider=Email).
Do not raise other password rules or enable reauthentication requirements as part
of this fix: those need separate client compatibility checks. Use an expendable
staging account to check compromised-password rejection on signup/reset, safe
error handling, existing-account login and Google login before rollout. Do not
test compromised passwords against real accounts. No client-side password lookup
has been added; it would not enforce server policy.

Both `public.moneymate_claim_daily_points()` and `public.moneymate_points_status()`
are owned by postgres, SECURITY DEFINER, empty search_path, and executable by
authenticated but not anon. Neither has a PUBLIC grant. Both check auth.uid()
and the authenticated role and constrain points access to that UUID. Claims can
still add ten non-redeemable points per UTC day to the caller's own account.
This is retired-feature exposure; these definitions do not show cross-account
financial access, arbitrary points awards or a redemption path. No exploitation
or absence of exploitation is claimed; user data and request logs were not read.

The live migration history ends at `20261013014333`; retirement is absent.
Catalog dependencies, other stored function bodies, policies and triggers showed
no callers of these two RPCs. This is not proof about external/dynamic callers.
Current website and Android sources omit both RPCs. Points retirement predates
the 0.3.7 source. The installed Play binary and all older installations have not
been independently inspected. Verify they no longer display/claim points before
applying revocation; old points screens would receive permission errors.

Keep `commit_account_snapshot(bigint,jsonb)` and `moneymate_admin_overview()`:
their authenticated SECURITY DEFINER grants are intentional. Snapshot writes
check identity, account existence, validation and revision; admin overview checks
membership in a private table. Do not revoke all public functions or switch these
to invoker just to silence the advisor. The three RLS-with-no-policy notices on
admin/points tables are consistent with their denied direct client access.

## Candidate change and validation

`20261014101910_verify_retired_points_permissions.sql` repeats only the existing
retirement revocations, in a transaction, and fails if effective anon or
authenticated execution remains (including inherited grants). It changes no
function body, table, historical points, snapshot grant or Auth setting.
The earlier retire-only script remains historical; use this verified candidate.

The pinned PGlite PostgreSQL test creates synthetic local Auth/schema fixtures,
applies the repository's real points, snapshot-validation and admin SQL, creates
a historical claim, applies the candidate twice, and verifies both roles receive
42501 from both RPCs and direct points table reads. Historical rows remain exactly
equal; admin access still works only for members. The updated snapshot SQL test
checks all five record types, revisions, malformed input, owner isolation and
retired RPC denial. Web CI now runs for Supabase and dependency-file changes.

This is isolated database staging, not hosted Supabase/Auth/PostgREST or a physical
device test. No hosted development branches exist. No paid branch was created.

## Owner rollout steps

1. Review the draft PR and confirm current Play 0.3.7 and the deployed website do
   not call these RPCs; account for older testers before removing their points UI.
2. In an approved staging project, apply only the targeted candidate SQL, then run
   `supabase/tests/snapshot-safety.sql`. It creates synthetic identities and rolls
   them back; never run this write-based check on live production under the
   read-only authorization. Check signed-in REST calls return 42501/permission
   errors and real client login/sync/admin behavior remains healthy.
3. After owner approval, apply only this targeted SQL to production. Do not use a
   blanket database push: the repository lacks historical baseline migrations and
   contains older abbreviated/same-date migration filenames. Their reconciliation
   is outside this small security change. Do not replay points creation afterward,
   because it grants execution again.
4. Read-only verification: run has_function_privilege for anon/authenticated on
   both exact signatures; all four must be false. Run security advisors: the two
   points warnings should disappear; intentional sync/admin warnings remain.
5. Password protection needs the separate owner plan/settings action above; its
   advisor warning remains until that action is completed.

Emergency rollback would regrant EXECUTE on only these two functions to
authenticated after owner approval. That restores the retired-feature exposure;
it is not a default rollout step. No data restoration is needed.

PRs #12, #21 and #22 remain separate. No merge, Pages deployment, Play upload or
release is part of this change.
