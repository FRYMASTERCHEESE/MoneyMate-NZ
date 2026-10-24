-- REVIEW-ONLY STAGING SCHEMA. Do not apply or enable bank linking before provider
-- approval, privacy/retention review, server-side consent and revocation handling.
-- No bank secrets or provider tokens belong in these tables or in GitHub Pages.
-- Provider import writes must come from an authenticated, reviewed server-side worker
-- using the service role, never directly from browser/Android user payloads.

create table if not exists public.moneymate_bank_connections (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null references auth.users(id) on delete cascade,
  provider text not null check (provider in ('akahu','basiq')),
  provider_connection_id text not null check (length(provider_connection_id) between 1 and 256),
  country text not null check (country in ('NZ','AU')),
  status text not null default 'pending' check (status in ('pending','connected','expired','revoked')),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  unique (user_id,provider,provider_connection_id),
  unique (id,user_id,provider),
  check ((provider='akahu' and country='NZ') or (provider='basiq' and country='AU'))
);

create table if not exists public.moneymate_bank_imports (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null references auth.users(id) on delete cascade,
  connection_id uuid not null,
  provider text not null check (provider in ('akahu','basiq')),
  provider_account_id text not null check (length(provider_account_id) between 1 and 256),
  provider_transaction_id text not null check (length(provider_transaction_id) between 1 and 256),
  currency text not null check (currency in ('NZD','AUD')),
  amount_cents bigint not null,
  posted_at timestamptz not null,
  description text not null check (length(description) between 1 and 500),
  imported_at timestamptz not null default now(),
  constraint moneymate_bank_imports_connection_owner_fk
    foreign key (connection_id,user_id,provider)
    references public.moneymate_bank_connections(id,user_id,provider) on delete cascade,
  unique (connection_id,provider_account_id,provider_transaction_id),
  check ((provider='akahu' and currency='NZD') or (provider='basiq' and currency='AUD'))
);

create index if not exists moneymate_bank_imports_owner_date_idx
  on public.moneymate_bank_imports(user_id,posted_at desc);
create index if not exists moneymate_bank_connections_owner_idx
  on public.moneymate_bank_connections(user_id);

alter table public.moneymate_bank_connections enable row level security;
alter table public.moneymate_bank_imports enable row level security;
-- Users can see only their own links/import history. They cannot forge imports,
-- approve/revoke consent, or edit connection status by direct REST/PostgREST calls.
revoke all on public.moneymate_bank_connections from public,anon,authenticated;
revoke all on public.moneymate_bank_imports from public,anon,authenticated;
grant select on public.moneymate_bank_connections to authenticated;
grant select on public.moneymate_bank_imports to authenticated;

drop policy if exists moneymate_bank_connections_owner_read on public.moneymate_bank_connections;
create policy moneymate_bank_connections_owner_read
  on public.moneymate_bank_connections for select to authenticated
  using (user_id = (select auth.uid()));
drop policy if exists moneymate_bank_imports_owner_read on public.moneymate_bank_imports;
create policy moneymate_bank_imports_owner_read
  on public.moneymate_bank_imports for select to authenticated
  using (user_id = (select auth.uid()));

comment on table public.moneymate_bank_imports is
  'Staging-only read-only bank transaction ledger. Not the app budget transaction table; never silently count twice.';
comment on table public.moneymate_bank_connections is
  'Staging-only consent metadata. No bank password, user access token or provider secret stored here.';
