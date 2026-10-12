import test from 'node:test';
import assert from 'node:assert/strict';
import {parseCsv,planBankCsv} from '../bank-csv.mjs';

const csv='Date,Amount,Description,Currency\r\n12/10/2026,-50.25,"PAK'nSAVE, Matamata",NZD\r\n13/10/2026,300.10,Salary,NZD\r\n';
test('quoted merchant, CRLF and precise NZD integer cents, with category suggestions',()=>{
 const p=planBankCsv(csv,[],'Everyday');
 assert.equal(p.total,2);assert.equal(p.skipped,0);
 assert.deepEqual(p.toAdd[0],{date:'2026-10-12',kind:'Expense',category:'Groceries',note:"Bank CSV (Everyday): PAK'nSAVE, Matamata",cents:5025});
 assert.equal(p.toAdd[1].cents,30010);assert.equal(p.toAdd[1].kind,'Income');
});
test('a second import of same CSV is idempotent, overlapping files import only the new entries',()=>{
 const first=planBankCsv(csv,[],'Everyday');
 const repeated=planBankCsv(csv,first.toAdd,'Everyday');
 assert.equal(repeated.toAdd.length,0);assert.equal(repeated.skipped,2);
 const extended=csv+'14/10/2026,-7.50,BP fuel,NZD\r\n';
 const next=planBankCsv(extended,first.toAdd,'Everyday');
 assert.equal(next.skipped,2);assert.equal(next.toAdd.length,1);assert.equal(next.toAdd[0].category,'Transport');
});
test('two truly identical rows remain two transactions, reimport skips both',()=>{
 const text='Date,Amount,Description\n2026-10-12,-5.00,Cafe\n2026-10-12,-5.00,Cafe\n';
 const first=planBankCsv(text,[],'Daily');assert.equal(first.toAdd.length,2);
 const repeated=planBankCsv(text,first.toAdd,'Daily');assert.equal(repeated.toAdd.length,0);assert.equal(repeated.skipped,2);
 const other=planBankCsv(text,first.toAdd,'Savings');assert.equal(other.toAdd.length,2);
});
test('split debit credit amounts, unambiguous dates and MoneyMate export formats',()=>{
 const text='Transaction Date,Debit,Credit,Details\n01/02/2026,12.34,,Electric power\n02/02/2026,,99.50,Salary\n';
 const p=planBankCsv(text);
 assert.equal(p.toAdd[0].date,'2026-02-01');assert.equal(p.toAdd[0].cents,1234);
 assert.equal(p.toAdd[1].kind,'Income');assert.equal(p.toAdd[1].cents,9950);
 const own='Date,Type,Category,Note,Amount NZD\n2026-10-12,Expense,Housing,Rent,560.00\n';
 assert.equal(planBankCsv(own).toAdd[0].kind,'Expense');
});
test('AUD and invalid/mismatched currencies are rejected without partial import',()=>{
 for(const t of [
  'Date,Amount AUD,Description\n2026-10-12,-5,Cafe\n',
  'Date,Amount,Description,Currency\n2026-10-12,-5,Cafe,AUD\n',
  'Date,Amount,Description,Status\n2026-10-12,-5,Cafe,pending\n',
  'Date,Amount,Description\n2026-02-30,-5,Cafe\n',
  'Date,Amount,Description\n2026-10-12,1.999,Cafe\n',
  'Date,Amount,Description\n2026-10-12,1e6,Cafe\n',
  'Date,Amount,Description\n2026-10-12,,Cafe\n',
  'Date,Amount,Description\n2026-10-12,"-5,Cafe\n',
  'Date,Amount,Description\n2026-10-12,-5\n'
 ])assert.throws(()=>planBankCsv(t),undefined,t);
});
test('bounds, duplicate header and wrong account nickname reject input',()=>{
 assert.throws(()=>planBankCsv('Date,Amount,amount,Description\n2026-10-12,1,2,Fuel\n'));
 assert.throws(()=>planBankCsv(csv,[],''));
 assert.throws(()=>planBankCsv(csv,[],'bad\nlabel'));
 assert.throws(()=>planBankCsv(csv+' '.repeat(2_000_000)));
 const over='Date,Amount,Description\n'+Array(5001).fill('2026-10-12,1.00,A').join('\n');
 assert.throws(()=>planBankCsv(over));
});
test('quoted embedded newline and escaped double quotes parse as one cell',()=>{
 const parsed=parseCsv('Date,Amount,Description\n2026-10-12,-2.00,"Said ""hello"",\ntoday"\n');
 assert.equal(parsed[1][2],'Said "hello",\ntoday');
});
