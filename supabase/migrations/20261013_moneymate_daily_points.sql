-- MoneyMate NZ non-redeemable points. Repeat daily claims cannot earn extra points.
create table if not exists public.moneymate_point_balances (
 user_id uuid primary key references auth.users(id) on delete cascade,
 points bigint not null default 0 check (points >= 0),
 updated_at timestamptz not null default now()
);
create table if not exists public.moneymate_point_daily_claims (
 user_id uuid not null references auth.users(id) on delete cascade,
 claim_date date not null,
 points_awarded integer not null default 10 check (points_awarded = 10),
 claimed_at timestamptz not null default now(),
 primary key(user_id,claim_date)
);
alter table public.moneymate_point_balances enable row level security;
alter table public.moneymate_point_daily_claims enable row level security;
revoke all on public.moneymate_point_balances from public,anon,authenticated;
revoke all on public.moneymate_point_daily_claims from public,anon,authenticated;
create or replace function public.moneymate_points_status()
returns jsonb language plpgsql stable security definer set search_path='' as $$
declare v_user uuid;v_today date;
begin
 v_user:=(select auth.uid());
 if v_user is null or (select auth.role())<>'authenticated' then raise exception 'Authentication required' using errcode='42501';end if;
 v_today:=(now() at time zone 'UTC')::date;
 return pg_catalog.jsonb_build_object(
 'points',coalesce((select points from public.moneymate_point_balances where user_id=v_user),0),
 'daily_claimed',exists(select 1 from public.moneymate_point_daily_claims where user_id=v_user and claim_date=v_today),
 'daily_points',10,'period','UTC','redeemable',false);
end $$;
create or replace function public.moneymate_claim_daily_points()
returns jsonb language plpgsql volatile security definer set search_path='' as $$
declare v_user uuid;v_today date;v_awarded integer;v_balance bigint;
begin
 v_user:=(select auth.uid());
 if v_user is null or (select auth.role())<>'authenticated' then raise exception 'Authentication required' using errcode='42501';end if;
 v_today:=(now() at time zone 'UTC')::date;
 insert into public.moneymate_point_daily_claims(user_id,claim_date) values(v_user,v_today)
 on conflict(user_id,claim_date) do nothing returning points_awarded into v_awarded;
 if v_awarded is not null then
  insert into public.moneymate_point_balances(user_id,points) values(v_user,v_awarded)
  on conflict(user_id) do update set points=public.moneymate_point_balances.points+excluded.points,updated_at=now();
 end if;
 select points into v_balance from public.moneymate_point_balances where user_id=v_user;
 return pg_catalog.jsonb_build_object('points',coalesce(v_balance,0),'awarded',coalesce(v_awarded,0),
 'daily_claimed',true,'daily_points',10,'period','UTC','redeemable',false);
end $$;
revoke all on function public.moneymate_points_status() from public,anon;
revoke all on function public.moneymate_claim_daily_points() from public,anon;
grant execute on function public.moneymate_points_status() to authenticated;
grant execute on function public.moneymate_claim_daily_points() to authenticated;
