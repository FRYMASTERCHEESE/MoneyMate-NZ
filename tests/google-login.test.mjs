import test from "node:test";
import assert from "node:assert/strict";
import {readFileSync} from "node:fs";
const read=p=>readFileSync(new URL("../"+p,import.meta.url),"utf8");
test("Google login source is consistent across both website hosting paths",()=>{
 assert.equal(read("docs/web-app.js"),read("web-app.js"));
 const js=read("docs/web-app.js");
 for(const phrase of ["Continue with Google","Sign up with Google","code_challenge_method","grant_type=pkce","sessionStorage.removeItem(GOOGLE_PKCE_KEY)"]){
   assert.ok(js.includes(phrase),phrase);
 }
});
test("only public configuration is sent to website",()=>{
 for(const p of ["docs/account-config.json","account-config.json"]){
  const c=JSON.parse(read(p));
  assert.equal(c.googleEnabled,true);
  assert.match(c.publishableKey,/^sb_publishable_[A-Za-z0-9_-]+$/);
  assert.deepEqual(Object.keys(c).sort(),["googleEnabled","publishableKey","url"]);
 }
});
test("Google login alone does not enable financial syncing",()=>{
 const js=read("docs/web-app.js");
 assert.match(js,/cloud-enable/);
 assert.match(js,/if\(!auth\|\|!cloudMeta\?\.enabled\)return/);
 assert.match(js,/if\(!preApproved&&!window\.confirm\("Enable MoneyMate NZ cloud sync/);
 assert.match(js,/restoreLogin\(\)/);
});
