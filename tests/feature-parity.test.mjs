import test from "node:test";
import assert from "node:assert/strict";
import {readFileSync} from "node:fs";

const read=p=>readFileSync(new URL("../"+p,import.meta.url),"utf8");
const web=read("docs/web-app.js");
const activity=read("app/src/main/java/nz/moneymate/app/MainActivity.java");
const android=read("app/src/main/java/nz/moneymate/app/CloudSync.java");
const auth=read("app/src/main/java/nz/moneymate/app/AuthClient.java");

test("MoneyMate Android and website both support opt-in account syncing",()=>{
 assert.match(web,/Enable secure cloud sync/);
 assert.match(activity,/Enable secure cloud sync/);
 assert.match(web,/Sync now/);
 assert.match(activity,/Sync records now/);
 assert.match(web,/Use cloud copy after backup/);
 assert.match(activity,/Use cloud copy after backup/);
 assert.match(web,/moneymate_admin_overview/);
 assert.match(web,/async function restoreLogin\(\)/);
 assert.match(activity,/handleGoogleCallback/);
});
test("both platforms use same signed-in, revision-checked snapshot endpoints",()=>{
 for(const endpoint of ["read_account_snapshot","commit_account_snapshot"]){
  assert.ok(web.includes(endpoint),"Website missing "+endpoint);
  assert.ok(auth.includes(endpoint),"Android missing "+endpoint);
 }
 assert.match(web,/if\(!auth\|\|!cloudMeta\?\.enabled\)return/);
 assert.match(android,/if\(!state.enabled\)return/);
 assert.match(android,/if\(!state.fingerprint.equals\(current\)\)/);
 assert.match(web,/baseRevision:cloudMeta.revision,baseDigest:cloudMeta.digest/);
});
test("cloud changes cover matching financial record types and privacy safeguards",()=>{
 const backup=read("app/src/main/java/nz/moneymate/app/Store.java");
 for(const column of ["transactions","budgets","goals","bills","recurring"]){
  assert.ok(web.includes(column),column+" missing from website");
  assert.ok(android.includes(column)||backup.includes(column),column+" missing from Android");
 }
 assert.match(read("docs/profile-store.mjs"),/before-cloud-/);
 assert.match(android,/financial-recovery/);
 assert.match(activity,/CloudSync.recoveryCopy/);
 assert.match(read("docs/privacy.html"),/Optional account syncing/);
});
test("web and root publishing copies match and website retains existing profiles",()=>{
 assert.equal(web,read("web-app.js"));
 assert.equal(read("docs/sync-policy.mjs"),read("sync-policy.mjs"));
 assert.equal(read("docs/profile-store.mjs"),read("profile-store.mjs"));
 assert.match(web,/indexedDB.open\("MoneyMateNZWeb",2\)/);
 assert.match(web,/if\(!req.result.objectStoreNames.contains\("profiles"\)\)/);
});
