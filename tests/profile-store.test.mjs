import test from "node:test";
import assert from "node:assert/strict";
import {indexedDB} from "fake-indexeddb";
import {writeProfile} from "../docs/profile-store.mjs";
import {emptyData} from "../docs/web-core.mjs";

async function open(){return new Promise((resolve,reject)=>{
 const r=indexedDB.open(crypto.randomUUID(),2);
 r.onupgradeneeded=()=>{r.result.createObjectStore("profiles");r.result.createObjectStore("sync");};
 r.onsuccess=()=>resolve(r.result);r.onerror=()=>reject(r.error);
});}
async function read(db,store,key){return new Promise((resolve,reject)=>{
 const tx=db.transaction(store),r=tx.objectStore(store).get(key);
 tx.oncomplete=()=>resolve(r.result);tx.onerror=()=>reject(tx.error);
});}
function changed(name){const d=emptyData();d.goals.push({id:1,name,target:10000,saved:0});return d;}
test("two browser tabs cannot overwrite one another's saved financial changes",async()=>{
 const db=await open(),baseline=emptyData();
 await writeProfile(db,"A",baseline);
 const outcomes=await Promise.allSettled([
  writeProfile(db,"A",changed("tab one"),{expected:baseline}),
  writeProfile(db,"A",changed("tab two"),{expected:baseline})
 ]);
 assert.equal(outcomes.filter(r=>r.status==="fulfilled").length,1);
 assert.match(outcomes.find(r=>r.status==="rejected").reason.message,/another tab/);
 assert.equal((await read(db,"profiles","A")).goals[0].name,"tab one");db.close();
});
test("cloud replacement, outgoing recovery and revision metadata commit atomically",async()=>{
 const db=await open(),old=changed("local"),cloud=changed("cloud");
 await writeProfile(db,"A",old);await writeProfile(db,"B",changed("private B"));
 await writeProfile(db,"A",cloud,{expected:old,recovery:true,metadata:{enabled:true,revision:4,digest:"cloud"}});
 assert.deepEqual(await read(db,"profiles","A"),cloud);
 assert.equal((await read(db,"sync","A")).revision,4);
 const keys=await new Promise(resolve=>{const r=db.transaction("profiles").objectStore("profiles").getAllKeys();r.onsuccess=()=>resolve(r.result);});
 assert.deepEqual(await read(db,"profiles",keys.find(k=>k.startsWith("A-before-cloud-"))),old);
 assert.equal((await read(db,"profiles","B")).goals[0].name,"private B");db.close();
});
test("switching accounts or editing during a cloud download aborts the whole transaction",async()=>{
 const db=await open(),old=changed("local");await writeProfile(db,"A",old);
 await assert.rejects(writeProfile(db,"A",changed("cloud"),{expected:old,recovery:true,metadata:{revision:9},current:()=>false}),/changed/);
 assert.deepEqual(await read(db,"profiles","A"),old);assert.equal(await read(db,"sync","A"),undefined);db.close();
});
test("invalid backups cannot alter persisted records",async()=>{
 const db=await open(),old=changed("safe");await writeProfile(db,"A",old);
 assert.throws(()=>writeProfile(db,"A",{...old,goals:[{id:1,name:"bad",target:-1,saved:0}]}));
 assert.deepEqual(await read(db,"profiles","A"),old);db.close();
});
