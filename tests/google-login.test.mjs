import test from "node:test";
import assert from "node:assert/strict";
import {readFileSync} from "node:fs";
const read=p=>readFileSync(new URL("../"+p,import.meta.url),"utf8");
test("Google login source is consistent across both website hosting paths",()=>{
 assert.equal(read("docs/web-app.js"),read("web-app.js"));
 const js=read("docs/web-app.js");
 for(const phrase of ["Google sign-in via browser (backup)","Google sign-up via browser (backup)","code_challenge_method","grant_type=pkce","sessionStorage.removeItem(GOOGLE_PKCE_KEY)"]){
   assert.ok(js.includes(phrase),phrase);
 }
});
test("only public configuration is sent to website",()=>{
 for(const p of ["docs/account-config.json","account-config.json"]){
  const c=JSON.parse(read(p));
  assert.equal(c.googleEnabled,true);
  assert.match(c.publishableKey,/^sb_publishable_[A-Za-z0-9_-]+$/);
  assert.equal(c.googleClientId,"353755907837-67mnn6nil8smrpsej8ms5pcvm0i71nm6.apps.googleusercontent.com");
  assert.deepEqual(Object.keys(c).sort(),["googleClientId","googleEnabled","publishableKey","url"]);
 }
});
test("Google login alone does not enable financial syncing",()=>{
 const js=read("docs/web-app.js");
 assert.match(js,/cloud-enable/);
 assert.match(js,/if\(!auth\|\|!cloudMeta\?\.enabled\)return/);
 assert.match(js,/if\(!window\.confirm\("Enable MoneyMate NZ cloud sync/);
 assert.match(js,/restoreLogin\(\)/);
});

test("direct Google sign-in exchanges ID token via Supabase and preserves PKCE fallback",()=>{
 const js=read("docs/web-app.js");
 assert.ok(js.includes("https://accounts.google.com/gsi/client"));
 assert.ok(js.includes('identity.renderButton(target,'));
 assert.ok(js.includes('/auth/v1/token?grant_type=id_token'));
 assert.ok(js.includes('provider:"google",id_token:response.credential'));
 assert.ok(js.includes('async function signInWithGoogle()'));
 assert.ok(js.includes('async function finishGoogleSignin()'));
 assert.ok(js.includes('if(!auth&&config?.googleEnabled&&config?.googleClientId)'));
 assert.equal(read("docs/sw.js"),read("sw.js"));
 assert.match(read("sw.js"),/v12-google-direct-signin/);
});
