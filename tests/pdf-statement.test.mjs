import test from "node:test";
import assert from "node:assert/strict";
import {pdfStatementToCsv} from "../pdf-statement.mjs";
import {planBankCsv} from "../bank-csv.mjs";

const lines=[
 "MoneyMate Bank Statement NZD",
 "Date Description Amount Balance",
 "12/10/2026 Countdown Matamata -52.30 1,125.70",
 "13/10/2026 Salary +1,000.00 2,125.70"
];
test("signed NZD PDF text rows convert to existing additive CSV import safely",()=>{
 const csv=pdfStatementToCsv(lines);
 const p=planBankCsv(csv,[],"Everyday");
 assert.equal(p.total,2);
 assert.equal(p.toAdd[0].kind,"Expense");
 assert.equal(p.toAdd[0].cents,5230);
 assert.equal(p.toAdd[0].category,"Groceries");
 assert.equal(p.toAdd[1].kind,"Income");
 assert.equal(p.toAdd[1].cents,100000);
 const reimport=planBankCsv(csv,p.toAdd,"Everyday");
 assert.equal(reimport.toAdd.length,0);
 assert.equal(reimport.skipped,2);
});
test("debit credit identified by clear DR and CR markers only",()=>{
 const csv=pdfStatementToCsv(["Date Description Debit Credit Balance",
    "12/10/2026 Fuel 42.10 DR 1,100.50",
    "13/10/2026 Salary 1000.00 CR 2,100.50"]);
 const plan=planBankCsv(csv);
 assert.deepEqual(plan.toAdd.map(r=>r.kind),["Expense","Income"]);
 assert.deepEqual(plan.toAdd.map(r=>r.cents),[4210,100000]);
});
test("ambiguous balances and unsigned amounts always fail closed",()=>{
 const cases=[
  ["Date Description Debit Credit Balance","12/10/2026 Grocery 30.20 130.00"],
  ["Date Description Amount Balance","12/10/2026 Grocery 30.20 130.00"],
  ["Date Description Amount","12/10/2026 Grocery -30.20 130.00"],
  ["Date Description Amount","12/10/2026 Grocery -30.20 --"],
  ["Date Description Amount","12/10/2026 Grocery -30.20","13/10/2026 Cafe rubbish"],
  ["Date Description Amount","12/10/2026 Pending -20.00"]
 ];
 for(const rows of cases)assert.throws(()=>pdfStatementToCsv(rows),"No auto-approve for "+rows);
});
test("AUD currency and headers without a readable table fail",()=>{
 assert.throws(()=>pdfStatementToCsv(["Statement AUD","Date Description Amount","12/10/2026 Buy -5.00"]));
 assert.throws(()=>pdfStatementToCsv(["Total Balance","12/10/2026 Buy -5.00"]));
 assert.throws(()=>pdfStatementToCsv([]));
});
