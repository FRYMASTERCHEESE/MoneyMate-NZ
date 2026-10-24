import test from 'node:test';
import assert from 'node:assert/strict';
import {decimalToCents, normalizeBankTransaction, normalizeBankBatch} from '../supabase/functions/_shared/bank-import.mjs';

const nz = Object.freeze({_id:'trans_1', _account:'acc_1', date:'2026-10-12T12:00:00Z', description:'Groceries', amount:-50.25});
const au = Object.freeze({id:'tx-1', account:'account-1', status:'posted', postDate:'2026-10-12T09:00:00+10:00', description:'Fuel', amount:'-100.10', direction:'debit'});

test('money parser uses integer cents without binary floating point or rounding',()=>{
  assert.equal(decimalToCents('-50.25'),-5025);
  assert.equal(decimalToCents(0.3),30);
  assert.equal(decimalToCents('0.01'),1);
  assert.equal(decimalToCents('-0.01'),-1);
  assert.equal(decimalToCents(1000000),100000000);
  for (const invalid of ['1.999','1e5','NaN','Infinity','01.23',null,{},1.234,Number.NaN,Number.POSITIVE_INFINITY])
    assert.throws(()=>decimalToCents(invalid));
});

test('New Zealand Akahu settled transaction parses as signed NZD cents',()=>{
  assert.deepEqual(normalizeBankTransaction('akahu',nz,'NZD'),{
    provider:'akahu',providerAccountId:'acc_1',providerTransactionId:'trans_1',
    currency:'NZD',amountCents:-5025,postedAt:'2026-10-12T12:00:00.000Z',description:'Groceries'});
});

test('Australia Basiq posted transaction retains AUD and normalises timezone',()=>{
  const result=normalizeBankTransaction('basiq',au,'AUD');
  assert.equal(result.amountCents,-10010);
  assert.equal(result.postedAt,'2026-10-11T23:00:00.000Z');
  assert.equal(result.currency,'AUD');
  assert.equal(Object.hasOwn(result,'balance'),false);
});

test('rejects unsupported currencies, providers, pending status and wrong sign',()=>{
  assert.throws(()=>normalizeBankTransaction('akahu',nz,'AUD'));
  assert.throws(()=>normalizeBankTransaction('basiq',au,'NZD'));
  assert.throws(()=>normalizeBankTransaction('other',nz,'NZD'));
  assert.throws(()=>normalizeBankTransaction('basiq',{...au,status:'pending'},'AUD'));
  assert.throws(()=>normalizeBankTransaction('basiq',{...au,amount:'50.00'},'AUD'));
  assert.throws(()=>normalizeBankTransaction('akahu',{...nz,date:'yesterday'},'NZD'));
  assert.throws(()=>normalizeBankTransaction('akahu',{...nz,description:'\nsecret'},'NZD'));
});

test('duplicate IDs are idempotent but conflicting duplicates fail closed',()=>{
  assert.equal(normalizeBankBatch('akahu',[nz,{...nz}], 'NZD').length,1);
  assert.throws(()=>normalizeBankBatch('akahu',[nz,{...nz,amount:-52}],'NZD'));
  assert.equal(normalizeBankBatch('basiq',[au],'AUD').length,1);
  assert.throws(()=>normalizeBankBatch('akahu',Array(501).fill(nz),'NZD'));
});

test('changing bank account never merges a transaction ID across accounts',()=>{
  const list=normalizeBankBatch('akahu',[nz,{...nz,_account:'acc_2'}],'NZD');
  assert.equal(list.length,2);
});
