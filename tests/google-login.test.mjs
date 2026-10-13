import test from "node:test";
import assert from "node:assert/strict";
import {readFileSync} from "node:fs";
const read=p=>readFileSync(new URL("../"+p,import.meta.url),"utf8");
test("Google login source is consistent across both website hosting paths",()=>{
 assert.equal(read("docs/web-app.js"),read("web-app.js"));
 const js=read("docs/web-app.js");
 for(const phrase of ["Google sign-in via browser (backup)","Sign up with Google","code_challenge_method","grant_type=pkce","sessionStorage.removeItem(GOOGLE_PKCE_KEY)"]){
   assert.ok(js.includes(phrase),phrase);
 }
});
test("only public configuration is sent to website",()=>{
 for(const p of ["docs/account-config.json","account-config.json"]){
  const c=JSON.parse(read(p));
  assert.equal(c.googleEnabled,true);
  assert.match(c.googleClientId,/^[0-9]+-[A-Za-z0-9_-]+[.]apps[.]googleusercontent[.]com$/);
  assert.match(c.publishableKey,/^sb_publishable_[A-Za-z0-9_-]+$/);
  assert.deepEqual(Object.keys(c).sort(),["googleClientId","googleEnabled","publishableKey","url"]);
 }
});
test("direct Google sign-in is added while legacy browser PKCE remains supported",()=>{
 const js=read("docs/web-app.js");
 assert.ok(js.includes("accounts.google.com/gsi/client"));
 assert.match(js,/sdk[.]renderButton/);
 assert.match(js,/grant_type=id_token/);
 assert.match(js,/provider:"google",id_token:response[.]credential/);
 assert.match(js,/grant_type=pkce/);
 const android=read("app/src/main/java/nz/moneymate/app/MainActivity.java");
 const auth=read("app/src/main/java/nz/moneymate/app/AuthClient.java");
 assert.ok(android.includes("Google sign-in via browser (backup)"));
 assert.match(auth,/grant_type=id_token/);
 assert.match(auth,/grant_type=pkce/);
});
test("Google login alone does not enable financial syncing",()=>{
 const js=read("docs/web-app.js");
 assert.match(js,/cloud-enable/);
 assert.match(js,/if\(!auth\|\|!cloudMeta\?\.enabled\)return/);
 assert.match(js,/if\(!window\.confirm\("Enable MoneyMate NZ cloud sync/);
 assert.match(js,/restoreLogin\(\)/);
});
