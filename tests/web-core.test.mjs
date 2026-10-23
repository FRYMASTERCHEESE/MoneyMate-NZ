import test from "node:test";
import assert from "node:assert/strict";
import {cents,nzd,iso,advance,emptyData,strictData,recordDue,totals,monthlyPlan,csv} from "../docs/web-core.mjs";
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
test("monthly plan separates category caps, unpaid bills and goal progress from cash flow",()=>{
 const data=emptyData();
 data.budgets=[{category:"Food",cents:10000},{category:"Transport",cents:5000}];
 data.transactions=[
  {id:1,kind:"Expense",category:"Food",note:"",date:"2026-10-01",cents:12000},
  {id:2,kind:"Expense",category:"Other",note:"",date:"2026-10-02",cents:30000},
  {id:3,kind:"Income",category:"Food",note:"",date:"2026-10-02",cents:50000},
  {id:4,kind:"Expense",category:"Transport",note:"",date:"2026-09-30",cents:5000}];
 data.bills=[{id:1,name:"Power",due:"2026-10-31",cents:12345},{id:2,name:"Phone",due:"2026-11-01",cents:5000}];
 data.goals=[{id:1,name:"Buffer",target:10000,saved:12000},{id:2,name:"Trip",target:10000,saved:1000}];
 const before=structuredClone(data);
 assert.deepEqual(monthlyPlan(data,"2026-10"),{budgetRemaining:3000,billsDue:12345,billCount:1,completedGoals:1});
 assert.equal(totals(data,"2026-10").remaining,8000);
 assert.deepEqual(data,before);
 data.budgets.pop();assert.equal(monthlyPlan(data,"2026-10").budgetRemaining,-2000);
});
