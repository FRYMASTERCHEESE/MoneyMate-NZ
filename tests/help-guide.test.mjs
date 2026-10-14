import test from "node:test";
import assert from "node:assert/strict";
import {readFileSync} from "node:fs";

const source=p=>readFileSync(new URL("../"+p,import.meta.url),"utf8");
test("published and root website help pages match exactly",()=>{
  assert.equal(source("help.html"),source("docs/help.html"));
});
test("user guide includes all essential website and Android workflows",()=>{
  const help=source("docs/help.html");
  for(const section of ["id=\"start\"","id=\"transactions\"","id=\"bank-csv\"","id=\"budgets\"","id=\"sync\"","id=\"backup\"","id=\"faq\""])
    assert.ok(help.includes(section),section);
  for(const label of ["Enable secure cloud sync","Save full","NZD","AUD","JSON backup","CSV","same account","Android"])
    assert.ok(help.toLowerCase().includes(label.toLowerCase()),label);
  assert.ok(!help.includes("do <strong>not</strong> automatically synchronize financial records"));
  assert.ok(help.includes("Restoring JSON replaces"));
});
test("Android help tab exists and warns before backup restore",()=>{
  const android=source("app/src/main/java/nz/moneymate/app/MainActivity.java");
  assert.match(android,/case "Help": help\(\);break/);
  assert.match(android,/"Account","Banking","Help"/);
  assert.match(android,/if\(isVerifiedAdmin\(\)\)navigation\.add\("Admin"\)/);
  assert.match(android,/private void help\(\)/);
  assert.match(android,/Backup all data \(JSON\)/);
  assert.match(android,/REPLACES existing profile records/);
  assert.match(android,/Account → Enable secure cloud sync/);
});
test("both website and Android correctly distinguish imported statements from live bank linking",()=>{
  const help=source("docs/help.html");
  const android=source("app/src/main/java/nz/moneymate/app/MainActivity.java");
  assert.match(help,/Import your NZD bank CSV or PDF statement for free/);
  assert.match(help,/does <em>not<\/em> automatically download/);
  assert.match(android,/Automatic live bank connections are not yet available/);
});
