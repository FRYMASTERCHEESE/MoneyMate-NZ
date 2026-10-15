import test from "node:test";
import assert from "node:assert/strict";
import {anzNzdStatementToCsv,looksLikeAnzNzStatement} from "../anz-statement.mjs";
import {pdfStatementToCsv} from "../pdf-statement.mjs";
import {planBankCsv} from "../bank-csv.mjs";

// Synthetic rows following the publicly documented ANZ NZ statement column headings.
// No real customer's statement, account number, identity or personal bank records.
const rows=[
 "ANZ Bank New Zealand Limited Statement of Accounts",
 "Statement period 10 Sep 2024 - 09 Dec 2024",
 "Date Transaction type and details Withdrawals Deposits Balance",
 "10 Sep Opening balance 9,656.67",
 "10 Sep AP Transfer from family 5.00 9,661.67",
 "10 Sep EP Groceries at Countdown 7.00 9,654.67",
 "11 Sep EP Fuel at BP 7.95 9,646.72"
];
test("ANZ NZ unsigned withdrawal/deposit columns reconciled with opening and running balances",()=>{
 assert.equal(looksLikeAnzNzStatement(rows),true);
 const csv=pdfStatementToCsv(rows);
 const result=planBankCsv(csv,[],"ANZ Everyday");
 assert.equal(result.total,3);
 assert.deepEqual(result.toAdd.map(x=>[x.date,x.kind,x.cents]),[
  ["2024-09-10","Income",500],["2024-09-10","Expense",700],["2024-09-11","Expense",795]
 ]);
 const repeat=planBankCsv(csv,result.toAdd,"ANZ Everyday");
 assert.equal(repeat.toAdd.length,0);
 assert.equal(repeat.skipped,3);
});

test("ANZ rejects impossible calendar dates and accepts a real leap day",()=>{
 const table=(year,row)=>[`Statement period 01 Jan ${year} - 31 Dec ${year}`,
  "Date Transaction type and details Withdrawals Deposits Balance","Opening balance 100.00",row+" Coffee 5.00 95.00"];
 for(const [year,date] of [[2025,"29 Feb 2025"],[2024,"30 Feb 2024"],[2024,"31 Apr 2024"]])
  assert.throws(()=>anzNzdStatementToCsv(table(year,date)));
 assert.match(anzNzdStatementToCsv(table(2024,"29 Feb 2024")),/2024-02-29/);
});
test("ANZ statement period resolves date without year across December-January boundary",()=>{
 const csv=anzNzdStatementToCsv([
  "Statement period 01 Dec 2025 - 31 Jan 2026",
  "Date Transaction type and details Withdrawals Deposits Balance",
  "01 Dec Opening balance 100.00",
  "31 Dec Groceries 20.00 80.00",
  "01 Jan Pay 50.00 130.00"
 ]);
 assert.deepEqual(planBankCsv(csv).toAdd.map(x=>x.date),["2025-12-31","2026-01-01"]);
});
test("ANZ carried-forward page balances are checked, not imported",()=>{
 const csv=anzNzdStatementToCsv([
  "Statement period 01 Oct 2026 - 31 Oct 2026",
  "Date Transaction type and details Withdrawals Deposits Balance",
  "01 Oct Opening balance 100.00",
  "02 Oct Coffee 5.00 95.00",
  "Balance carried forward 95.00",
  "Date Transaction type and details Withdrawals Deposits Balance",
  "Balance brought forward from previous page 95.00",
  "03 Oct Salary 10.00 105.00"
 ]);
 assert.equal(planBankCsv(csv).total,2);
});
test("ANZ rejects unclear statement period, opening balances, mismatch and AUD",()=>{
 const base=[
  "Statement period 10 Sep 2024 - 09 Dec 2024",
  "Date Transaction type and details Withdrawals Deposits Balance",
  "10 Sep Opening balance 100.00"
 ];
 for(const scenario of [
  ["Date Transaction type and details Withdrawals Deposits Balance","10 Sep Coffee 5.00 95.00"],
  ["Statement period 10 Sep 2024 - 09 Dec 2024","Date Transaction type and details Withdrawals Deposits Balance","10 Sep Coffee 5.00 95.00"],
  [...base,"10 Sep Coffee 5.00 90.00"],
  [...base,"10 Sep Coffee 5.00 95.00","11 Sep Fuel 4.00 92.00"],
  [...base,"10 Sep Coffee 5.00 95.00","Balance brought forward 93.00"],
  [...base,"10 Sep Fuel 5.00 95.00","This account is AUD"],
  [...base,"10 Sep Fuel 5.00 95.00","Statement period 01 Dec 2025 - 31 Dec 2025","12 Dec Opening balance 50.00"]
 ])assert.throws(()=>pdfStatementToCsv(scenario));
});
