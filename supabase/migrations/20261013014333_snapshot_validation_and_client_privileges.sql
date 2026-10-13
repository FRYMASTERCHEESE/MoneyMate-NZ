-- No stored records are rewritten. Tighten only future snapshot commits.
create or replace function public.commit_account_snapshot(p_expected_revision bigint, p_data jsonb)
returns jsonb language plpgsql security definer set search_path = ''
as $function$
declare
 v_user uuid; v_new bigint; v_current bigint;
 v_table text; v_row jsonb; v_key text; v_numbers text[]; v_texts text[];
 v_seen text[]; v_identity text; v_number numeric; v_date text;
begin
 v_user := auth.uid();
 if v_user is null or auth.role() is distinct from 'authenticated' then
  raise exception 'Authentication required' using errcode='28000';
 end if;
 if not exists(select 1 from auth.users where id=v_user) then
  raise exception 'Account unavailable' using errcode='28000';
 end if;
 if p_expected_revision is null or p_expected_revision<0 then raise exception 'Invalid revision'; end if;
 if p_data is null or pg_catalog.pg_column_size(p_data)>524288
  or pg_catalog.jsonb_typeof(p_data) is distinct from 'object'
  or p_data->'format' is distinct from '"MoneyMateNZ"'::jsonb
  or p_data->'version' is distinct from '1'::jsonb then
  raise exception 'Invalid or oversized snapshot';
 end if;
 foreach v_table in array array['transactions','budgets','goals','bills','recurring'] loop
  if pg_catalog.jsonb_typeof(p_data->v_table) is distinct from 'array' then raise exception 'Missing snapshot records'; end if;
  if pg_catalog.jsonb_array_length(p_data->v_table)>100000 then raise exception 'Too many records'; end if;
  case v_table
   when 'transactions' then v_numbers:=array['id','cents']; v_texts:=array['kind','category','note','date'];
   when 'budgets' then v_numbers:=array['cents']; v_texts:=array['category'];
   when 'goals' then v_numbers:=array['id','target','saved']; v_texts:=array['name'];
   when 'bills' then v_numbers:=array['id','cents']; v_texts:=array['name','due'];
   else v_numbers:=array['id','cents']; v_texts:=array['kind','category','note','next_date','frequency'];
  end case;
  v_seen:=array[]::text[];
  for v_row in select value from pg_catalog.jsonb_array_elements(p_data->v_table) loop
   if pg_catalog.jsonb_typeof(v_row) is distinct from 'object' then raise exception 'Invalid record'; end if;
   for v_key in select pg_catalog.jsonb_object_keys(v_row) loop
    if not(v_key=any(v_numbers||v_texts)) then raise exception 'Unexpected record field'; end if;
   end loop;
   foreach v_key in array v_numbers loop
    if pg_catalog.jsonb_typeof(v_row->v_key) is distinct from 'number' then raise exception 'Missing numeric field'; end if;
    v_number:=(v_row->>v_key)::numeric;
    if v_number<0 or v_number>9007199254740991 or v_number<>pg_catalog.trunc(v_number) then raise exception 'Invalid integer amount'; end if;
    if (v_key in ('id','target') or (v_key='cents' and v_table<>'budgets')) and v_number<1 then raise exception 'Positive value required'; end if;
   end loop;
   foreach v_key in array v_texts loop
    if pg_catalog.jsonb_typeof(v_row->v_key) is distinct from 'string' or pg_catalog.length(v_row->>v_key)>2000 then raise exception 'Invalid text'; end if;
    if v_key in ('name','category') and pg_catalog.btrim(v_row->>v_key)='' then raise exception 'Name required'; end if;
   end loop;
   v_identity:=case when v_table='budgets' then v_row->>'category' else v_row->>'id' end;
   if v_identity=any(v_seen) then raise exception 'Duplicate record'; end if;
   v_seen:=pg_catalog.array_append(v_seen,v_identity);
   if v_table in ('transactions','recurring') and v_row->>'kind' not in ('Income','Expense') then raise exception 'Invalid direction'; end if;
   if v_table='recurring' and v_row->>'frequency' not in ('Weekly','Fortnightly','Monthly') then raise exception 'Invalid repeat'; end if;
   v_date:=case v_table when 'transactions' then v_row->>'date' when 'bills' then v_row->>'due' when 'recurring' then v_row->>'next_date' else null end;
   if v_date is not null then
    if v_date !~ '^[0-9]{4}-[0-9]{2}-[0-9]{2}$' or pg_catalog.to_char(v_date::date,'YYYY-MM-DD')<>v_date then raise exception 'Invalid date'; end if;
   end if;
  end loop;
 end loop;
 if p_expected_revision=0 then
  insert into public.account_snapshots(user_id,revision,data) values(v_user,1,p_data)
  on conflict do nothing returning revision into v_new;
 else
  update public.account_snapshots set revision=revision+1,data=p_data,updated_at=now()
  where user_id=v_user and revision=p_expected_revision returning revision into v_new;
 end if;
 if v_new is not null then return pg_catalog.jsonb_build_object('success',true,'revision',v_new); end if;
 select revision into v_current from public.account_snapshots where user_id=v_user;
 return pg_catalog.jsonb_build_object('success',false,'revision',coalesce(v_current,0));
end;
$function$;
revoke all on function public.commit_account_snapshot(bigint,jsonb) from public,anon;
grant execute on function public.commit_account_snapshot(bigint,jsonb) to authenticated;
-- RLS does not protect TRUNCATE. Client roles never need these privileges.
revoke truncate, references, trigger on public.profiles,public.transactions,public.budgets,public.goals,public.bills,public.recurring from authenticated,anon;

