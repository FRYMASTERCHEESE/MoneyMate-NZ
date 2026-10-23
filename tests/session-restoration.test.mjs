import test from 'node:test';
import assert from 'node:assert/strict';
import vm from 'node:vm';
import {readFileSync} from 'node:fs';
import * as core from '../docs/web-core.mjs';
import {normalizeLoginSession,sessionInvalidated} from '../docs/auth-session.mjs';
const A='11111111-1111-4111-8111-111111111111',B='22222222-2222-4222-8222-222222222222';
const session=(expired=false)=>({access_token:'synthetic-access',refresh_token:'synthetic-refresh',expires_at:Math.floor(Date.now()/1000)+(expired?-60:3600),user:{id:A,email:'synthetic@example.invalid'}});
const source=readFileSync(new URL('../docs/web-app.js',import.meta.url),'utf8').replace(/^import .*;\r?\n/gm,'').replace('const toast=','let toast=').replace(/^bootstrap\(\);/m,'');
function harness(saved,options={}){
 let stored=structuredClone(saved),clears=0;const calls=[],owners=[];
 const element={addEventListener(){},removeEventListener(){}};
 const ctx=vm.createContext({...core,normalizeLoginSession,sessionInvalidated,
  loadLoginSession:async()=>{if(options.storageFailure)throw Error('Storage busy');return structuredClone(stored);},
  saveLoginSession:async s=>{stored=structuredClone(s);return s;},clearLoginSession:async()=>{clears++;stored=null;},
  confirm:()=>true,document:{querySelector:()=>element,querySelectorAll:()=>[],addEventListener(){}},window:{addEventListener(){}},setInterval(){}});
 const api=vm.runInContext(source+`
 config={};toast=()=>{};navigate=()=>{};
 switchOwner=async id=>{profile=id;onOwner(id);};cloudOnSignin=async()=>{};refreshAdminOverview=async()=>{};
 authApi=async(path,method,body)=>request(path,body);
 ({restore:restoreLogin,token:accessToken,signout,state:()=>({auth,profile}),setAuth:s=>{auth=s;}})
 `,Object.assign(ctx,{onOwner:id=>owners.push(id),request:async(path,body)=>{
  calls.push({path,body});if(options.failure)throw Object.assign(Error('Service error'),{httpStatus:options.failure});
  if(path.includes('refresh_token'))return {...session(),access_token:'rotated-access',refresh_token:'rotated-refresh',user:{id:options.changedIdentity?B:A}};
  return {id:options.changedIdentity?B:A};
 }}));
 return {api,calls,owners,get stored(){return stored;},get clears(){return clears;}};
}
test('website reload restores the same saved account without another login',async()=>{
 const h=harness(session());await h.api.restore();assert.equal(h.api.state().auth.user.id,A);assert.equal(h.calls[0].path,'/auth/v1/user');assert.equal(h.clears,0);
});
test('expired login rotates and persists tokens before opening account records',async()=>{
 const h=harness(session(true));await h.api.restore();assert.equal(h.stored.refresh_token,'rotated-refresh');assert.equal(h.api.state().auth.access_token,'rotated-access');assert.deepEqual(h.owners,[A]);
});
test('offline reload retains saved login and opens only its own local profile',async()=>{
 const h=harness(session(),{failure:503});await h.api.restore();assert.equal(h.stored.user.id,A);assert.equal(h.api.state().auth.user.id,A);assert.equal(h.clears,0);
});
test('temporary vault read failure never clears a saved login',async()=>{
 const h=harness(session(),{storageFailure:true});await h.api.restore();assert.equal(h.clears,0);assert.equal(h.stored.user.id,A);assert.equal(h.api.state().auth,null);
});
for(const failure of [400,401,403])test('revoked login '+failure+' locks retained records',async()=>{
 const h=harness(session(true),{failure});await h.api.restore();assert.equal(h.stored,null);assert.equal(h.api.state().auth,null);assert.deepEqual(h.owners,['guest']);
});
test('server identity mismatch cannot open a different account',async()=>{
 const h=harness(session(),{changedIdentity:true});await h.api.restore();assert.equal(h.stored,null);assert.ok(!h.owners.includes(B));
});
test('refresh identity mismatch locks records instead of opening the offline profile',async()=>{
 const h=harness(session(true),{changedIdentity:true});await h.api.restore();
 assert.equal(h.stored,null);assert.equal(h.api.state().auth,null);assert.deepEqual(h.owners,['guest']);
});
test('ordinary sign-out revokes only this session and hides retained account records',async()=>{
 const h=harness(session());h.api.setAuth(session());await h.api.signout();
 assert.equal(h.calls[0].path,'/auth/v1/logout?scope=local');
 assert.equal(h.stored,null);assert.equal(h.api.state().auth,null);assert.deepEqual(h.owners,['guest']);
});
test('offline sign-out clears saved login and hides retained account records',async()=>{
 const h=harness(session(),{failure:503});h.api.setAuth(session());await h.api.signout();
 assert.equal(h.stored,null);assert.equal(h.api.state().auth,null);assert.deepEqual(h.owners,['guest']);
});
test('simultaneous requests in one page rotate the refresh token once',async()=>{
 const h=harness(session(true));h.api.setAuth(session(true));const tokens=await Promise.all([h.api.token(),h.api.token(),h.api.token()]);assert.deepEqual(tokens,['rotated-access','rotated-access','rotated-access']);assert.equal(h.calls.length,1);assert.equal(h.stored.refresh_token,'rotated-refresh');
});
