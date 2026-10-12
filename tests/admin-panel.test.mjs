import test from "node:test";
import assert from "node:assert/strict";
import {readFileSync} from "node:fs";
const read=name=>readFileSync(new URL("../"+name,import.meta.url),"utf8");
test("admin site files are identical for Pages root and docs",()=>{
 for(const filename of ["index.html","web-app.js","web.css","sw.js","privacy.html"])
  assert.equal(read(filename),read("docs/"+filename));
});
test("administrator interface appears only after authenticated admin RPC",()=>{
 const js=read("docs/web-app.js");
 assert.match(js,/moneymate_admin_overview/);
 assert.match(js,/Authorization:"Bearer "\+token/);
 assert.match(js,/if\(next==="admin"&&!\(auth&&adminOverview\)\)/);
 assert.match(js,/adminOverview=null;auth=null/);
 assert.match(js,/stats\?\.role!=="admin"/);
 assert.match(js,/if\(!auth\|\|!adminOverview\)return heading/);
 assert.match(read("docs/index.html"),/data-page="admin" hidden/);
 assert.doesNotMatch(js,/coreyedge123@gmail.com/);
});
test("admin migration is scoped to member UUIDs and no financial records",()=>{
 const sql=read("supabase/migrations/20261012_moneymate_admin_overview.sql");
 assert.match(sql,/user_id uuid primary key references auth\.users\(id\)/);
 assert.match(sql,/enable row level security/);
 assert.match(sql,/from public, anon, authenticated/);
 assert.match(sql,/auth\.uid\(\)/);
 assert.match(sql,/auth\.role\(\) <> 'authenticated'/);
 assert.match(sql,/from public\.moneymate_admins where user_id=current_id/);
 assert.match(sql,/revoke all on function public\.moneymate_admin_overview\(\) from public, anon/);
 assert.doesNotMatch(sql,/coreyedge123@gmail.com|auth\.users\s+set\s+|delete\s+from\s+auth\.users/i);
});
