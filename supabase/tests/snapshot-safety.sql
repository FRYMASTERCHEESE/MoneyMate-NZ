-- Synthetic identities and records exist only within this rolled-back transaction.
begin;
do $test$
declare
 a uuid:=gen_random_uuid(); b uuid:=gen_random_uuid();
 snapshot jsonb:='{"format":"MoneyMateNZ","version":1,"transactions":[{"id":1,"cents":1234,"kind":"Expense","category":"Food","note":"Synthetic test","date":"2026-10-13"}],"budgets":[{"category":"Food","cents":2000}],"goals":[{"id":1,"name":"Test","target":5000,"saved":100}],"bills":[{"id":1,"name":"Test bill","cents":100,"due":"2026-11-01"}],"recurring":[{"id":1,"cents":100,"kind":"Expense","category":"Food","note":"","next_date":"2026-11-01","frequency":"Weekly"}]}'::jsonb;
 reply jsonb; bad jsonb; rejected boolean; v_rpc text;
begin
 insert into auth.users(id,aud,role,email,email_confirmed_at,raw_app_meta_data,raw_user_meta_data,created_at,updated_at)
 values(a,'authenticated','authenticated',a::text||'@example.invalid',now(),'{}','{}',now(),now()),
       (b,'authenticated','authenticated',b::text||'@example.invalid',now(),'{}','{}',now(),now());
 perform set_config('request.jwt.claims',jsonb_build_object('sub',a,'role','authenticated')::text,true);
 execute 'set local role authenticated';
 reply:=public.commit_account_snapshot(0,snapshot);
 if reply->>'success'<>'true' or reply->>'revision'<>'1' then raise exception 'Initial commit failed'; end if;
 if public.read_account_snapshot()->'data' is distinct from snapshot then raise exception 'Round trip failed'; end if;
 reply:=public.commit_account_snapshot(0,snapshot);
 if reply->>'success'<>'false' then raise exception 'Stale create accepted'; end if;
 reply:=public.commit_account_snapshot(1,snapshot);
 if reply->>'revision'<>'2' then raise exception 'Second device write failed'; end if;
 reply:=public.commit_account_snapshot(1,snapshot);
 if reply->>'success'<>'false' or reply->>'revision'<>'2' then raise exception 'Stale edit accepted'; end if;
 foreach bad in array array['{}'::jsonb,snapshot-'bills',jsonb_set(snapshot,'{goals,0,target}','1.5'),jsonb_set(snapshot,'{transactions,0,date}','"2026-02-30"'),jsonb_set(snapshot,'{transactions,0,cents}','"1234"'),jsonb_set(snapshot,'{transactions,0,kind}','"Transfer"')] loop
  rejected:=false;
  begin perform public.commit_account_snapshot(2,bad); exception when others then rejected:=true; end;
  if not rejected then raise exception 'Malformed snapshot accepted'; end if;
 end loop;
 if public.read_account_snapshot()->>'revision'<>'2' then raise exception 'Invalid data changed revision'; end if;
 foreach v_rpc in array array['moneymate_claim_daily_points','moneymate_points_status'] loop
  rejected:=false;
  begin execute format('select public.%I()',v_rpc); exception when insufficient_privilege then rejected:=true; end;
  if not rejected then raise exception 'Retired points RPC executable: %',v_rpc; end if;
 end loop;
 perform set_config('request.jwt.claims',jsonb_build_object('sub',b,'role','authenticated')::text,true);
 if public.read_account_snapshot()->>'revision'<>'0' then raise exception 'User isolation failed'; end if;
 if exists(select 1 from public.account_snapshots) then raise exception 'Snapshot RLS leaked another account'; end if;
 reply:=public.commit_account_snapshot(0,snapshot);
 if reply->>'revision'<>'1' then raise exception 'Second account commit failed'; end if;
 if has_table_privilege('authenticated','public.transactions','TRUNCATE') then raise exception 'Unsafe table grant'; end if;
 if has_function_privilege('anon','public.commit_account_snapshot(bigint,jsonb)','EXECUTE') then raise exception 'Anonymous commit executable'; end if;
 rejected:=false;
 begin update public.account_snapshots set revision=99; exception when insufficient_privilege then rejected:=true; end;
 if not rejected then raise exception 'Direct snapshot write allowed'; end if;
 execute 'reset role';
end
$test$;
rollback;
select 'PASS: all five record types, revision conflicts, invalid snapshots, RLS, retired points denial and isolation; all test data rolled back' as verification;
