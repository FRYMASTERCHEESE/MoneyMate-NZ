import {emptyData,strictData} from "./web-core.mjs";

// Compare and replace in ONE IndexedDB transaction: another tab cannot slip
// a financial edit between the comparison and the write. Resolve on commit.
export function writeProfile(db,owner,next,{expected,recovery=false,metadata,current=()=>true}={}) {
 const safe=strictData(next),baseline=expected===undefined?undefined:JSON.stringify(strictData(expected));
 return new Promise((resolve,reject)=>{
  const tx=db.transaction(metadata===undefined?["profiles"]:["profiles","sync"],"readwrite");
  const store=tx.objectStore("profiles");let failure;
  const request=store.get(owner);
  request.onsuccess=()=>{
   try {
    if(!current())throw Error("Account or records changed during saving. Nothing was replaced.");
    const previous=strictData(request.result||emptyData());
    if(baseline!==undefined&&JSON.stringify(previous)!==baseline)
     throw Error("Records changed in another tab. Reload before editing; the saved records are safe.");
    if(recovery)store.put(previous,owner+"-before-cloud-"+Date.now()+"-"+crypto.randomUUID());
    store.put(safe,owner);
    if(metadata!==undefined)tx.objectStore("sync").put(metadata,owner);
   }catch(error){failure=error;tx.abort();}
  };
  tx.oncomplete=()=>resolve(safe);
  tx.onerror=tx.onabort=()=>reject(failure||tx.error||Error("Financial save interrupted."));
 });
}
