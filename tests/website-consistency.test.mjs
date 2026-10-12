import test from "node:test";
import assert from "node:assert/strict";
import {readFileSync} from "node:fs";
const read=p=>readFileSync(new URL("../"+p,import.meta.url),"utf8");

test("desktop and public website share same sync client",()=>{
 assert.equal(read("web-app.js"),read("docs/web-app.js"));
 assert.equal(read("docs/sync-preview/sync-policy.mjs"),read("docs/sync-policy.mjs"));
 assert.equal(read("sync-policy.mjs"),read("docs/sync-policy.mjs"));
});
test("password registration and recovery both offer 8 characters",()=>{
 for (const p of ["docs/web-app.js","docs/sync-preview/web-app.js","web-app.js"]) {
  const code=read(p);
  assert.match(code,/Password \(8–128 characters\)/);
  assert.match(code,/New password \(8–128 chars\)/);
  assert.match(code,/password\.length<8/);
  assert.doesNotMatch(code,/password\.length<12|Password \(12–128/);
 }
});
test("no admin Supabase secrets in client configuration",()=>{
 for(const p of ["docs/account-config.json","account-config.json"]){
  const config=JSON.parse(read(p));
  assert.match(config.url,/^https:\/\/[a-z0-9-]+\.supabase\.co$/);
  assert.match(config.publishableKey,/^sb_publishable_/);
  assert.equal(typeof config.googleEnabled,"boolean");
  assert.equal(Object.keys(config).sort().join(","),"googleEnabled,publishableKey,url");
 }
});
