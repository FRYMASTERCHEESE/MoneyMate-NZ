-- After client rollout only; preserve all past points data.
revoke execute on function public.moneymate_claim_daily_points() from public, anon, authenticated;
revoke execute on function public.moneymate_points_status() from public, anon, authenticated;
