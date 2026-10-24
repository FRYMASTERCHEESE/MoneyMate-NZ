import test from "node:test";
import assert from "node:assert/strict";
import {readFileSync} from "node:fs";
const read=p=>readFileSync(new URL("../"+p,import.meta.url),"utf8");
test("offline cache refreshes for new planning features without clearing browser data",()=>{
 assert.equal(read("sw.js"),read("docs/sw.js"));
 assert.match(read("docs/sw.js"),/moneymate-nz-web-v17-fortnight-planning/);
 assert.match(read("docs/sw.js"),/caches\.delete\(key\)/);
});
test("published website paths match and show no points UI",()=>{
 for(const p of ["index.html","web-app.js","web.css","help.html","privacy.html"])
  assert.equal(read(p),read("docs/"+p));
 const html=read("docs/index.html"),js=read("docs/web-app.js");
 assert.match(html,/data-page="banking"/);
 assert.match(js,/function bankingPage\(\)/);
 assert.doesNotMatch(html,/data-page="rewards"/);
 assert.doesNotMatch(js,/rewardsPage|points-claim|go-rewards|moneymate_claim_daily_points|moneymate_points_status/);
 assert.doesNotMatch(read("docs/help.html"),/Claim 10 points|id="points"/);
 assert.match(read("docs/privacy.html"),/Historical points data/);
});
test("Android points interface and RPC helpers are absent but sync is present",()=>{
 const activity=read("app/src/main/java/nz/moneymate/app/MainActivity.java");
 const auth=read("app/src/main/java/nz/moneymate/app/AuthClient.java");
 assert.doesNotMatch(activity,/case "Rewards"|private void rewards\(|claimRewards|loadRewards|MoneyMate Points/);
 assert.doesNotMatch(auth,/moneymate_claim_daily_points|moneymate_points_status/);
 assert.match(auth,/read_account_snapshot/);
 assert.match(activity,/private void banking\(\)/);
 assert.match(activity,/private void help\(\)/);
});
test("historic points data remains intact and revocation is non-destructive",()=>{
 const prior=read("supabase/migrations/20261013_moneymate_daily_points.sql");
 assert.match(prior,/primary key\(user_id,claim_date\)/);
 const retired=read("supabase/migrations/20261013_retire_moneymate_points.sql");
 assert.match(retired,/revoke execute on function public\.moneymate_claim_daily_points\(\)/i);
 assert.match(retired,/revoke execute on function public\.moneymate_points_status\(\)/i);
 assert.doesNotMatch(retired,/drop table|truncate|delete from/i);
});
test("automatic bank connections still not live",()=>{
 const js=read("docs/web-app.js"),a=read("app/src/main/java/nz/moneymate/app/MainActivity.java");
 assert.match(js,/Not available yet/);assert.match(js,/Provider approval required/);
 assert.match(a,/NOT LIVE/);assert.match(js,/Akahu/);assert.match(js,/Basiq/);
 assert.doesNotMatch(js,/bank_password|bankPassword|client_secret/);
});
