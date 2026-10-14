import test from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {PGlite} from '@electric-sql/pglite';
const read = path => readFileSync(new URL('../'+path, import.meta.url),'utf8');

test('retired points permissions preserve history, deny RPCs and preserve snapshot sync', async () => {
 const db = new PGlite();
 try {
  // Isolated PostgreSQL fixture. No network, real identities or live database credentials.
  await db.exec(`
   create role anon; create role authenticated;
   create schema auth;
   create table auth.users(id uuid primary key,aud text,role text,email text,email_confirmed_at timestamptz,raw_app_meta_data jsonb,raw_user_meta_data jsonb,created_at timestamptz,updated_at timestamptz);
   create function auth.uid() returns uuid language sql stable as $$ select (current_setting('request.jwt.claims',true)::jsonb->>'sub')::uuid $$;
   create function auth.role() returns text language sql stable as $$ select current_setting('request.jwt.claims',true)::jsonb->>'role' $$;
   grant usage on schema auth to anon,authenticated;
   create table public.account_snapshots(user_id uuid primary key references auth.users(id),revision bigint not null,data jsonb not null,updated_at timestamptz default now());
   alter table public.account_snapshots enable row level security;
   create policy own_snapshot on public.account_snapshots for select to authenticated using(user_id=(select auth.uid()));
   grant select on public.account_snapshots to authenticated;
   create function public.read_account_snapshot() returns jsonb language sql stable set search_path='' as $$
    select coalesce((select pg_catalog.jsonb_build_object('revision',s.revision,'data',s.data) from public.account_snapshots s where s.user_id=(select auth.uid())),pg_catalog.jsonb_build_object('revision',0));
   $$;
   revoke all on function public.read_account_snapshot() from public,anon;
   grant execute on function public.read_account_snapshot() to authenticated;
   create table public.profiles(id uuid); create table public.transactions(id bigint);
   create table public.budgets(id bigint); create table public.goals(id bigint);
   create table public.bills(id bigint); create table public.recurring(id bigint);
  `);
  await db.exec(read('supabase/migrations/20261013014333_snapshot_validation_and_client_privileges.sql'));
  await db.exec(read('supabase/migrations/20261013_moneymate_daily_points.sql'));
  await db.exec(read('supabase/migrations/20261012_moneymate_admin_overview.sql'));
  const user = '00000000-0000-4000-8000-000000000001';
  await db.exec(`insert into auth.users(id) values('${user}'); select set_config('request.jwt.claims','{"sub":"${user}","role":"authenticated"}',false); set role authenticated;`);
  assert.equal((await db.query('select public.moneymate_claim_daily_points() as result')).rows[0].result.awarded,10);
  await db.exec('reset role');
  await db.exec(`insert into public.moneymate_admins(user_id) values('${user}')`);
  await db.exec('set role authenticated');
  const overview = (await db.query('select public.moneymate_admin_overview() as result')).rows[0].result;
  assert.equal(overview.role,'admin');
  await db.exec('reset role');
  const history = (await db.query('select * from public.moneymate_point_balances')).rows;
  const claims = (await db.query('select * from public.moneymate_point_daily_claims')).rows;
  const migration = read('supabase/migrations/20261014101910_verify_retired_points_permissions.sql');
  await db.exec(migration);
  await db.exec(migration); // Repeat application must be safe.
  for (const role of ['anon','authenticated']) {
   await db.exec(`set role ${role}`);
   for (const fn of ['moneymate_claim_daily_points','moneymate_points_status'])
    await assert.rejects(db.query(`select public.${fn}()`),e=>e.code==='42501');
   for (const table of ['moneymate_point_balances','moneymate_point_daily_claims'])
    await assert.rejects(db.query(`select * from public.${table}`),e=>e.code==='42501');
   await db.exec('reset role');
  }
  assert.deepEqual((await db.query('select * from public.moneymate_point_balances')).rows,history);
  assert.deepEqual((await db.query('select * from public.moneymate_point_daily_claims')).rows,claims);
  await db.exec('set role authenticated');
  const afterOverview = (await db.query('select public.moneymate_admin_overview() as result')).rows[0].result;
  delete overview.generated_at; delete afterOverview.generated_at;
  assert.deepEqual(afterOverview,overview);
  await db.exec(`select set_config('request.jwt.claims','{"sub":"00000000-0000-4000-8000-000000000002","role":"authenticated"}',false)`);
  await assert.rejects(db.query('select public.moneymate_admin_overview()'),e=>e.code==='42501');
  await db.exec('reset role');
  await db.exec(read('supabase/tests/snapshot-safety.sql'));
  // A membership grant outside this migration's scope must fail closed and
  // roll back BOTH direct revocations, rather than leave a partial change.
  await db.exec(`
   create role legacy_points_access;
   grant legacy_points_access to authenticated;
   grant execute on function public.moneymate_claim_daily_points() to legacy_points_access, authenticated;
   grant execute on function public.moneymate_points_status() to authenticated;
  `);
  const grantsBefore = (await db.query(`select proname,proacl::text as acl from pg_proc where proname in ('moneymate_claim_daily_points','moneymate_points_status') order by proname`)).rows;
  await assert.rejects(db.exec(migration),/permissions remain executable/);
  await db.exec('rollback');
  assert.deepEqual((await db.query(`select proname,proacl::text as acl from pg_proc where proname in ('moneymate_claim_daily_points','moneymate_points_status') order by proname`)).rows,grantsBefore);
  assert.deepEqual((await db.query('select * from public.moneymate_point_balances')).rows,history);
  assert.deepEqual((await db.query('select * from public.moneymate_point_daily_claims')).rows,claims);
  await db.exec('revoke legacy_points_access from authenticated');
  await db.exec(migration);
  assert.equal((await db.query(`select has_function_privilege('authenticated','public.moneymate_claim_daily_points()','EXECUTE') as allowed`)).rows[0].allowed,false);
 } finally { await db.close(); }
});
