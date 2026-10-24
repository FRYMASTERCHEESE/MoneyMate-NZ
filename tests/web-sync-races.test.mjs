import test from "node:test";
import assert from "node:assert/strict";
import {readFileSync} from "node:fs";
import vm from "node:vm";
import {IDBFactory} from "fake-indexeddb";
import * as core from "../docs/web-core.mjs";
import {writeProfile} from "../docs/profile-store.mjs";
import {decideSync} from "../docs/sync-policy.mjs";

const A="11111111-1111-4111-8111-111111111111",B="22222222-2222-4222-8222-222222222222";
function records(name){const d=core.emptyData();d.goals.push({id:1,name,target:10000,saved:0});return d;}
function deferred(){let resolve;const promise=new Promise(r=>resolve=r);return {promise,resolve};}
function app(options={}){
 const consentPrompts=[];
 const element={addEventListener(){},removeEventListener(){},close(){},classList:{add(){},remove(){}}};
 const context=vm.createContext({...core,writeProfile,decideSync,consentPrompts,indexedDB:new IDBFactory(),crypto,TextEncoder,structuredClone,
  FormData:class {get(key){return {category:"Synthetic",note:"Test",amount:"12.34",date:"2026-10-13"}[key];}},
  document:{querySelector:()=>element,querySelectorAll:()=>[],addEventListener(){},visibilityState:"visible"},
  window:{addEventListener(){},confirm:prompt=>{consentPrompts.push(prompt);return options.consent!==false;}},setInterval(){},setTimeout(){},clearTimeout(){}});
 const source=readFileSync(new URL("../docs/web-app.js",import.meta.url),"utf8").replace(/^import .*;\r?\n/gm,"").replace(/^bootstrap\(\);/m,"");
 return vm.runInContext(source+`
 render=()=>{};
 const performSync=syncCloud;let lastSync=Promise.resolve();
 syncCloud=(...args)=>lastSync=performSync(...args);
 ({
  async init(owner,records,metadata){profile=owner;profileEpoch++;auth={user:{id:owner}};data=strictData(records);cloudMeta=metadata;await dbWrite(owner,data);profileReady=true;},
  mock(read,commit){cloudRead=read;cloudCommit=commit;},
  async metadata(records,revision){return {enabled:true,revision,digest:await signature(records)};},
  sync:syncCloud,enable:enableCloud,restore:chooseCloudCopy,save,read:dbRead,due:refreshDueEntries,settled:()=>lastSync,signInCloud:cloudOnSignin,prompts:()=>consentPrompts,
  async putGuest(records){await dbWrite('guest',strictData(records));},
  submit(){modalKind="expense";return submitModal({preventDefault(){}});},
  state(){return {profile,data,cloudMeta,cloudStatus,cloudBusy};}
 })`,context);
}
test("rapid repeated Save submissions create exactly one financial entry",async()=>{
 const h=app();await h.init(A,core.emptyData(),null);
 await Promise.all([h.submit(),h.submit()]);
 const saved=await h.read(A);assert.equal(saved.transactions.length,1);assert.equal(saved.transactions[0].cents,1234);
});
test("foreground recurrence catch-up is persisted once even with overlapping wakeups",async()=>{
 const h=app(),data=core.emptyData();
 const today=new Date(),date=[today.getFullYear(),String(today.getMonth()+1).padStart(2,"0"),String(today.getDate()).padStart(2,"0")].join("-");
 data.recurring=[{id:1,kind:"Expense",category:"Rent",note:"Scheduled",cents:50000,next_date:date,frequency:"Weekly"}];
 await h.init(A,data,null);
 await Promise.all([h.due(),h.due()]);await h.due();
 const saved=await h.read(A);assert.equal(saved.transactions.length,1);
 assert.equal(saved.transactions[0].date,date);assert.ok(saved.recurring[0].next_date>date);
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

test("two account profiles exchange every financial record type through revision-checked sync",async()=>{
 const first=app(),second=app(),full=core.emptyData();
 full.transactions.push({id:1,kind:"Expense",category:"Food",note:"Synthetic",cents:1234,date:"2026-10-13"});
 full.budgets.push({category:"Food",cents:30000});
 full.goals.push({id:1,name:"Emergency",target:100000,saved:5000});
 full.bills.push({id:1,name:"Power",cents:12000,due:"2026-10-20"});
 full.recurring.push({id:1,kind:"Income",category:"Salary",note:"Synthetic",cents:100000,next_date:"2026-10-20",frequency:"Weekly"});
 let cloud={revision:0,data:null};
 const read=async()=>structuredClone(cloud),commit=async(revision,data)=>{
  if(revision!==cloud.revision)return {success:false,revision:cloud.revision};
  cloud={revision:revision+1,data:structuredClone(data)};return {success:true,revision:cloud.revision};
 };
 await first.init(A,full,await first.metadata(core.emptyData(),0));first.mock(read,commit);
 await first.sync();assert.equal(cloud.revision,1);
 await second.init(A,core.emptyData(),await second.metadata(core.emptyData(),0));second.mock(read,commit);
 await second.sync();assert.deepEqual(core.strictData(await second.read(A)),full);
 const edited=structuredClone(full);edited.goals[0].saved=7000;edited.bills=[];
 await second.save(edited);
 // Await the actual upload, rather than assuming a count of event-loop turns is enough.
 await second.settled();
 assert.equal(second.state().cloudBusy,false);await first.sync();
 assert.equal(cloud.revision,2);assert.deepEqual(core.strictData(await first.read(A)),edited);
});


test("empty signed-in website automatically downloads an existing cloud copy without uploading",async()=>{
 const h=app(),remote=records("car");
 remote.goals[0].saved=2000;remote.goals[0].target=200000;
 await h.init(A,core.emptyData(),null);
 h.mock(async()=>({revision:7,data:remote}),async()=>assert.fail("Empty browser must never upload over existing cloud"));
 await h.signInCloud();
 const saved=await h.read(A);
 assert.deepEqual(core.strictData(saved),remote);
 assert.equal(h.state().cloudMeta.enabled,true);
 assert.equal(h.state().cloudMeta.revision,7);
 assert.equal(h.prompts().length,0,"Existing account cloud consent requires no second upload prompt");
 assert.match(h.state().cloudStatus,/automatically linked/i);
});
test("auto-link never overwrites records saved while cloud request is pending",async()=>{
 const h=app(),cloud=deferred();
 await h.init(A,core.emptyData(),null);
 h.mock(()=>cloud.promise,async()=>assert.fail("No cloud upload expected"));
 const waiting=h.signInCloud();
 await h.save(records("car added while waiting"));
 cloud.resolve({revision:3,data:records("from cloud")});
 await waiting;
 assert.equal((await h.read(A)).goals[0].name,"car added while waiting");
 assert.equal(h.state().cloudMeta?.enabled??false,false);
});
test("existing local goal and different cloud goal require explicit conflict review",async()=>{
 const h=app(),local=records("car"),remote=records("other device");
 local.goals[0].saved=2000;remote.goals[0].saved=5000;
 await h.init(A,local,null);
 h.mock(async()=>({revision:2,data:remote}),async()=>assert.fail("A conflict must not upload"));
 await h.signInCloud();
 assert.equal((await h.read(A)).goals[0].name,"car");
 assert.equal((await h.read(A)).goals[0].saved,2000);
 assert.equal(h.state().cloudMeta.enabled,false);
 assert.match(h.state().cloudStatus,/conflict/i);
 assert.equal(h.prompts().length,1);
});
test("declining first-time cloud consent never uploads private financial records",async()=>{
 const h=app({consent:false});await h.init(A,records("private car"),null);
 h.mock(async()=>({revision:0}),async()=>assert.fail("Consent was declined; upload forbidden"));
 await h.signInCloud();
 assert.equal((await h.read(A)).goals[0].name,"private car");
 assert.equal(h.state().cloudMeta.enabled,false);
 assert.equal(h.prompts().length,1);
 // Consent should not be repeatedly requested during every automatic status check.
 await h.signInCloud(true);
 assert.equal(h.prompts().length,1);
});

test("consented phone goal can fill an existing EMPTY cloud snapshot by revision",async()=>{
 const h=app(),car=records("car"),cloud={revision:3,data:core.emptyData()};
 car.goals[0].saved=2000;car.goals[0].target=200000;
 await h.init(A,car,null);
 h.mock(async()=>structuredClone(cloud),async(expected,snapshot)=>{
  assert.equal(expected,3);assert.equal(snapshot.goals[0].name,"car");
  assert.equal(core.emptyData().goals.length,cloud.data.goals.length,"Only the empty cloud copy is replaced");
  cloud.revision=4;cloud.data=structuredClone(snapshot);
  return {success:true,revision:4};
 });
 await h.enable();
 assert.equal(h.state().cloudMeta.revision,4);
 assert.equal(h.state().cloudMeta.enabled,true);
 assert.equal(cloud.data.goals[0].saved,2000);
 assert.equal((await h.read(A)).goals[0].name,"car");
});
test("failed empty-cloud CAS never overwrites a competing device or the phone goal",async()=>{
 const h=app(),car=records("car"),changedByOther=records("other device");
 await h.init(A,car,null);
 h.mock(async()=>({revision:3,data:core.emptyData()}),async(expected,snapshot)=>{
  assert.equal(expected,3);
  assert.equal(snapshot.goals[0].name,"car");
  return {success:false,revision:4};
 });
 await h.enable();
 assert.equal((await h.read(A)).goals[0].name,"car");
 assert.equal(h.state().cloudMeta?.enabled??false,false);
 assert.match(h.state().cloudStatus,/changed/i);
});

test("account auto-links existing cloud goal while retaining separate guest records",async()=>{
 const h=app(),guest=records("separate guest car"),remote=records("car");
 guest.goals[0].saved=1000;remote.goals[0].saved=2000;remote.goals[0].target=200000;
 await h.init(A,core.emptyData(),null);
 await h.putGuest(guest);
 h.mock(async()=>({revision:3,data:remote}),async()=>assert.fail("No upload permitted during account download"));
 await h.signInCloud();
 assert.equal((await h.read(A)).goals[0].name,"car");
 assert.equal((await h.read(A)).goals[0].saved,2000);
 assert.equal((await h.read("guest")).goals[0].name,"separate guest car");
 assert.equal((await h.read("guest")).goals[0].saved,1000);
 assert.equal(h.state().cloudMeta.enabled,true);
 assert.match(h.state().cloudStatus,/guest records are preserved/i);
 assert.equal(h.prompts().length,0);
});
