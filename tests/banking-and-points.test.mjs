import test from "node:test";
import assert from "node:assert/strict";
import {readFileSync} from "node:fs";
const read=p=>readFileSync(new URL("../"+p,import.meta.url),"utf8");
test("both published website paths include matching points and bank navigation",()=>{
 for(const p of ["index.html","web-app.js","web.css","privacy.html"])
  assert.equal(read(p),read("docs/"+p));
 const js=read("docs/web-app.js");
 assert.match(read("docs/index.html"),/data-page="banking"/);
 assert.match(read("docs/index.html"),/data-page="rewards"/);
 assert.match(js,/function bankingPage\(\)/);
 assert.match(js,/function rewardsPage\(\)/);
 assert.match(js,/moneymate_claim_daily_points/);
 assert.match(js,/moneymate_points_status/);
});
test("earnings are enforced server side and bound to signed-in account",()=>{
 const js=read("docs/web-app.js");
 assert.match(js,/moneymate_claim_daily_points","POST",\{\},await accessToken\(\)/);
 const a=read("app/src/main/java/nz/moneymate/app/AuthClient.java");
 assert.match(a,/moneymate_claim_daily_points/);
 const sql=read("supabase/migrations/20261013_moneymate_daily_points.sql");
 assert.match(sql,/primary key\(user_id,claim_date\)/);
 assert.match(sql,/on conflict\(user_id,claim_date\) do nothing/);
 assert.match(sql,/auth\.uid\(\)/);
 assert.match(sql,/revoke all on function public\.moneymate_claim_daily_points\(\) from public,anon/);
 assert.match(sql,/revoke all on public\.moneymate_point_balances from public,anon,authenticated/);
 assert.match(sql,/redeemable',false/);
});
test("bank connections are explicitly not active and require provider onboarding",()=>{
 const js=read("docs/web-app.js");
 const android=read("app/src/main/java/nz/moneymate/app/MainActivity.java");
 assert.match(js,/Not available yet/);
 assert.match(js,/Provider approval required/);
 assert.match(android,/NOT LIVE/);
 assert.match(js,/Akahu/);
 assert.match(js,/Basiq/);
 assert.doesNotMatch(js,/bank_password|bankPassword|client_secret/);
});
