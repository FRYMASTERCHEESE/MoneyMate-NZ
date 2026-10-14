import test from "node:test";
import assert from "node:assert/strict";
import {cents,nzd,iso,advance,emptyData,strictData,recordDue,totals,csv,nextId} from "../docs/web-core.mjs";
test("large valid profiles allocate IDs without exhausting the argument stack",()=>{
 const rows=Array.from({length:100000},(_,i)=>({id:i+1}));
 assert.equal(nextId(rows),100001);
 assert.throws(()=>nextId([{id:Number.MAX_SAFE_INTEGER}]),/ID/);
});
test("NZD integer cents are exact",()=>{
  assert.equal(cents("$1,234.50"),123450);
  assert.equal(cents("0"),0);
  assert.throws(()=>cents("1.234"));
  assert.throws(()=>cents("-1"));
  assert.match(nzd(1250),/12.50/);
});
test("strict date checks and monthly recurrence",()=>{
  assert.throws(()=>iso("2026-02-30"));
  assert.equal(advance("2026-01-31","Monthly"),"2026-02-28");
  assert.equal(advance("2028-01-31","Monthly"),"2028-02-29");
  assert.equal(advance("2026-10-12","Fortnightly"),"2026-10-26");
});
test("repeating records post once and preserve data",()=>{
  const d=emptyData();
  d.recurring.push({id:1,kind:"Expense",category:"Rent",note:"",cents:50000,next_date:"2026-10-01",frequency:"Weekly"});
  const first=recordDue(d,"2026-10-12");
  assert.equal(first.created,2);
  assert.equal(first.data.transactions.length,2);
  assert.equal(first.data.recurring[0].next_date,"2026-10-15");
  const second=recordDue(first.data,"2026-10-12");
  assert.equal(second.created,0);
  assert.equal(d.transactions.length,0);
  assert.equal(totals(first.data,"2026-10").expense,100000);
});
test("Android JSON export round-trips with goals, bills, budgets",()=>{
  const d=emptyData();
  d.budgets.push({category:"Groceries",cents:15000});
  d.goals.push({id:1,name:"Holiday",target:50000,saved:5000});
  d.bills.push({id:2,name:"Power",cents:5000,due:"2026-11-01"});
  d.transactions.push({id:1,kind:"Income",category:"Wages",note:"test",cents:9900,date:"2026-10-12"});
  assert.deepEqual(strictData(JSON.parse(JSON.stringify(d))),d);
  assert.match(csv(d),/"Wages"/);
  const invalid=structuredClone(d);invalid.transactions[0].extra="unwanted";
  assert.throws(()=>strictData(invalid));
  const dupe=structuredClone(d);dupe.transactions.push(dupe.transactions[0]);
  assert.throws(()=>strictData(dupe));
});
test("invalid backups never count as compatible",()=>{
  assert.throws(()=>strictData({format:"Other",version:1}));
  assert.throws(()=>strictData({...emptyData(),recurring:[{id:1,frequency:"Daily"}]}));
});
