-- MoneyMate NZ administrator authorization. No hard-coded account emails.
create table if not exists public.moneymate_admins (
  user_id uuid primary key references auth.users(id) on delete cascade,
  granted_at timestamptz not null default now()
);
alter table public.moneymate_admins enable row level security;
revoke all on table public.moneymate_admins from public, anon, authenticated;

create or replace function public.moneymate_admin_overview()
returns jsonb
language plpgsql
stable
security definer
set search_path = ''
as $$
declare
  current_id uuid;
begin
  current_id := auth.uid();
  if current_id is null or auth.role() <> 'authenticated' then
    raise exception 'Authentication required' using errcode='42501';
  end if;
  if not exists (select 1 from public.moneymate_admins where user_id=current_id) then
    raise exception 'Administrator access denied' using errcode='42501';
  end if;
  return pg_catalog.jsonb_build_object(
    'role','admin',
    'generated_at',pg_catalog.now(),
    'registered_users',(select pg_catalog.count(*) from auth.users),
    'verified_users',(select pg_catalog.count(*) from auth.users where email_confirmed_at is not null),
    'google_users',(select pg_catalog.count(*) from auth.users where raw_app_meta_data->'providers' ? 'google'),
    'registrations_last_7_days',(select pg_catalog.count(*) from auth.users where created_at>=pg_catalog.now()-interval '7 days'),
    'cloud_sync_accounts',(select pg_catalog.count(*) from public.account_snapshots),
    'latest_registration_at',(select pg_catalog.max(created_at) from auth.users)
  );
end
$$;
revoke all on function public.moneymate_admin_overview() from public, anon;
grant execute on function public.moneymate_admin_overview() to authenticated;
