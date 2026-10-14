import test from "node:test";
import assert from "node:assert/strict";
import {readFileSync} from "node:fs";
import vm from "node:vm";
import {IDBFactory} from "fake-indexeddb";
import * as core from "../docs/web-core.mjs";
import {writeProfile} from "../docs/profile-store.mjs";
import {decideSync,shouldLinkExistingCloud} from "../docs/sync-policy.mjs";

const A="11111111-1111-4111-8111-111111111111",B="22222222-2222-4222-8222-222222222222";
function records(name){const d=core.emptyData();d.goals.push({id:1,name,target:10000,saved:0});return d;}
function deferred(){let resolve;const promise=new Promise(r=>resolve=r);return {promise,resolve};}
function app(){
 const element={addEventListener(){},removeEventListener(){},close(){},classList:{add(){},remove(){}}};
 const context=vm.createContext({...core,writeProfile,decideSync,shouldLinkExistingCloud,indexedDB:new IDBFactory(),crypto,TextEncoder,structuredClone,
  FormData:class {get(key){return {category:"Synthetic",note:"Test",amount:"12.34",date:"2026-10-13"}[key];}},
  document:{querySelector:()=>element,querySelectorAll:()=>[],addEventListener(){},visibilityState:"visible"},
  window:{addEventListener(){},confirm:()=>true},confirm:()=>true,setInterval(){},setTimeout(){},clearTimeout(){}});
 const source=readFileSync(new URL("../docs/web-app.js",import.meta.url),"utf8").replace(/^import .*;\r?\n/gm,"").replace(/^bootstrap\(\);/m,"");
 return vm.runInContext(source+`
 render=()=>{};
 ({
  async init(owner,records,metadata){profile=owner;profileEpoch++;auth={user:{id:owner}};data=strictData(records);cloudMeta=metadata;await dbWrite(owner,data);},
  mock(read,commit){cloudRead=read;cloudCommit=commit;},
  async metadata(records,revision){return {enabled:true,revision,digest:await signature(records)};},
  sync:syncCloud,onSignin:cloudOnSignin,enable:enableCloud,restore:chooseCloudCopy,save,read:dbRead,copyGuest,
  mockGuest(read){const original=dbRead;dbRead=id=>id==="guest"?read():original(id);},
  submit(){modalKind="expense";return submitModal({preventDefault(){}});},
  state(){return {profile,data,cloudMeta,cloudStatus};}
 })`,context);
}
test("guest copy cannot overwrite another account after an asynchronous read",async()=>{
 const h=app(),guest=deferred();await h.init(A,core.emptyData(),null);
 h.mockGuest(()=>guest.promise);const work=h.copyGuest();
 await h.init(B,records("B private"),null);guest.resolve(records("Guest"));
 await assert.rejects(work,/changed/);
 assert.equal((await h.read(B)).goals[0].name,"B private");
 assert.equal((await h.read(A)).goals.length,0);
});
test("guest copy cannot overwrite edits made while reading guest data",async()=>{
 const h=app(),guest=deferred();await h.init(A,core.emptyData(),null);
 h.mockGuest(()=>guest.promise);const work=h.copyGuest();
 await h.save(records("new local edit"));guest.resolve(records("Guest"));
 await assert.rejects(work,/changed/);
 assert.equal((await h.read(A)).goals[0].name,"new local edit");
});
test("rapid repeated Save submissions create exactly one financial entry",async()=>{
 const h=app();await h.init(A,core.emptyData(),null);
 await Promise.all([h.submit(),h.submit()]);
 const saved=await h.read(A);assert.equal(saved.transactions.length,1);assert.equal(saved.transactions[0].cents,1234);
});
test("late upload completion cannot attach account A's sync metadata to account B",async()=>{
 const h=app(),old=records("old"),fresh=records("edited"),upload=deferred(),started=deferred();
 await h.init(A,fresh,await h.metadata(old,1));
 h.mock(async()=>({revision:1,data:old}),async()=>{started.resolve();return upload.promise;});
 const work=h.sync();await started.promise;
 await h.init(B,records("B private"),null);upload.resolve({success:true,revision:2});await work;
 assert.equal(h.state().cloudMeta,null);assert.equal(h.state().data.goals[0].name,"B private");
 assert.equal((await h.read(A)).goals[0].name,"edited");
});
test("edits made while a cloud restore is downloading are preserved",async()=>{
 const h=app(),old=records("old"),download=deferred();
 await h.init(A,old,await h.metadata(old,1));h.mock(()=>download.promise,()=>assert.fail("Unexpected upload"));
 const work=h.restore();await h.save(records("new local edit"));
 download.resolve({revision:2,data:records("cloud")});await work;
 assert.equal((await h.read(A)).goals[0].name,"new local edit");
 assert.equal(h.state().cloudMeta.revision,1);
});
test("automatic download detects unsynced local edits made during the request",async()=>{
 const h=app(),old=records("old"),download=deferred();
 await h.init(A,old,await h.metadata(old,1));
 h.mock(()=>download.promise,()=>assert.fail("Unexpected upload"));
 const work=h.sync();await h.save(records("new edit"));
 download.resolve({revision:2,data:records("cloud")});await work;
 assert.equal((await h.read(A)).goals[0].name,"new edit");
 assert.match(h.state().cloudStatus,/conflict/i);
});
test("initial consent download stops when the account changes",async()=>{
 const h=app(),download=deferred();await h.init(A,core.emptyData(),null);
 h.mock(()=>download.promise,()=>assert.fail("Unexpected upload"));
 const work=h.enable();await h.init(B,records("private B"),null);
 download.resolve({revision:1,data:records("A cloud")});await work;
 assert.equal(h.state().cloudMeta,null);assert.equal((await h.read(B)).goals[0].name,"private B");
});

test("existing consented cloud copy attaches only on an empty second device",()=>{
 assert.equal(shouldLinkExistingCloud({remoteRevision:2,localEmpty:true}),true);
 assert.equal(shouldLinkExistingCloud({remoteRevision:0,localEmpty:true}),false);
 assert.equal(shouldLinkExistingCloud({remoteRevision:2,localEmpty:false}),false);
 assert.equal(shouldLinkExistingCloud({enabled:true,remoteRevision:2,localEmpty:true}),false);
 assert.throws(()=>shouldLinkExistingCloud({remoteRevision:-1,localEmpty:true}),/revision/i);
});

test("signing into an empty browser profile restores an existing consented cloud copy",async()=>{
 const h=app(),remote=records("cloud backed account");
 await h.init(A,core.emptyData(),null);
 h.mock(async()=>({revision:3,data:remote}),()=>assert.fail("Must never upload over existing cloud copy"));
 await h.onSignin();
 assert.equal(h.state().cloudMeta.enabled,true);
 assert.equal(h.state().cloudMeta.revision,3);
 assert.equal(h.state().data.goals[0].name,"cloud backed account");
 assert.equal((await h.read(A)).goals[0].name,"cloud backed account");
});
test("signing into an occupied browser profile never overwrites existing financial data",async()=>{
 const h=app(),local=records("keep existing records"),remote=records("other device");
 await h.init(A,local,null);
 h.mock(async()=>({revision:8,data:remote}),()=>assert.fail("Unexpected automatic upload"));
 await h.onSignin();
 assert.equal(h.state().cloudMeta,null);
 assert.equal((await h.read(A)).goals[0].name,"keep existing records");
 assert.match(h.state().cloudStatus,/Cloud copy available/);
});
