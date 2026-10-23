// A browser-private, origin-scoped login vault. Never put tokens in URLs, cookies or GitHub.
const DB="MoneyMateNZPrivateAuth",STORE="sessions",KEY="main";
export function normalizeLoginSession(session,expectedUserId=null){
 if(!session||typeof session!=="object"||Array.isArray(session))throw Error("Invalid login session");
 const user=session.user,id=user?.id;
 if(typeof id!=="string"||!/^[0-9a-f]{8}-(?:[0-9a-f]{4}-){3}[0-9a-f]{12}$/i.test(id)||
    typeof session.access_token!=="string"||!session.access_token||
    typeof session.refresh_token!=="string"||!session.refresh_token)
    throw Error("Invalid login session");
 // A server-confirmed identity change is an auth failure, never an offline fallback.
 if(expectedUserId!==null&&expectedUserId!==id)throw Object.assign(Error("Account identity changed"),{httpStatus:401});
 const expiry=Number.isSafeInteger(session.expires_at)&&session.expires_at>0?session.expires_at:
    Number.isSafeInteger(session.expires_in)&&session.expires_in>0?
    Math.floor(Date.now()/1000)+session.expires_in:0;
 if(!expiry)throw Error("Invalid login expiry");
 return {
  access_token:session.access_token,refresh_token:session.refresh_token,
  expires_at:expiry,user:{id,email:typeof user.email==="string"?user.email:""}
 };
}
export function sessionInvalidated(status){return status===400||status===401||status===403;}
function openVault(){
 return new Promise((resolve,reject)=>{
  const request=indexedDB.open(DB,1);
  request.onupgradeneeded=()=>{if(!request.result.objectStoreNames.contains(STORE))request.result.createObjectStore(STORE);};
  request.onsuccess=()=>resolve(request.result);
  request.onerror=()=>reject(request.error||Error("Could not access saved login"));
  request.onblocked=()=>reject(Error("Login storage is busy"));
 });
}
async function operate(mode,action){
 const db=await openVault();
 try{return await new Promise((resolve,reject)=>{
   const tx=db.transaction(STORE,mode),store=tx.objectStore(STORE);
   let result;
   const request=action(store);
   request.onsuccess=()=>{result=request.result;};
   tx.oncomplete=()=>resolve(result);
   tx.onerror=()=>reject(tx.error||Error("Login storage unavailable"));
   tx.onabort=()=>reject(tx.error||Error("Login storage interrupted"));
  });}finally{db.close();}
}
export async function loadLoginSession(){
 const stored=await operate("readonly",store=>store.get(KEY));
 return stored?normalizeLoginSession(stored):null;
}
export async function saveLoginSession(session){
 const valid=normalizeLoginSession(session);
 await operate("readwrite",store=>store.put(valid,KEY));
 return valid;
}
export async function clearLoginSession(){
 await operate("readwrite",store=>store.delete(KEY));
}
