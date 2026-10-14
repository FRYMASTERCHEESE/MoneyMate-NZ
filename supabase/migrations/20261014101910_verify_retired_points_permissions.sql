-- Apply only after checking the deployed clients no longer call these retired RPCs.
-- Correct live grant drift without changing function bodies or historical records.
begin;
revoke execute on function public.moneymate_claim_daily_points() from public, anon, authenticated;
revoke execute on function public.moneymate_points_status() from public, anon, authenticated;
do $verify$
begin
 if has_function_privilege('anon','public.moneymate_claim_daily_points()','EXECUTE')
 or has_function_privilege('authenticated','public.moneymate_claim_daily_points()','EXECUTE')
 or has_function_privilege('anon','public.moneymate_points_status()','EXECUTE')
 or has_function_privilege('authenticated','public.moneymate_points_status()','EXECUTE') then
  raise exception 'Retired points RPC permissions remain executable';
 end if;
end $verify$;
commit;
