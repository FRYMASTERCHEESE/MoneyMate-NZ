import test from 'node:test';
import assert from 'node:assert/strict';
import vm from 'node:vm';
import * as core from '../docs/web-core.mjs';
import {readFileSync} from 'node:fs';
const source=readFileSync(new URL('../docs/web-app.js',import.meta.url),'utf8').replace(/^import .*;\r?\n/gm,'').replace('const toast=','let toast=').replace(/^bootstrap\(\);/m,'');
function harness(search='',hash='',options={}){
 const messages=[],removed=[],requests=[],urls=[];let restored=0,attached=0;
 const element={addEventListener(){},removeEventListener(){}};
 const ctx=vm.createContext({setInterval(){},...core,URL,URLSearchParams,crypto,TextEncoder,Uint8Array,btoa,
  document:{querySelector:()=>element,querySelectorAll:()=>[],addEventListener(){}},window:{addEventListener(){}},
  location:{search,hash,origin:'https://example.invalid',pathname:'/MoneyMate-NZ/',assign:u=>urls.push(u)},
  history:{replaceState:(_a,_b,u)=>urls.push(u)},sessionStorage:{setItem(){},getItem:()=> options.missingVerifier?null:'test-verifier',removeItem:k=>removed.push(k)}});
 const api=vm.runInContext(source+`
 config={url:'https://efdegdlsrkxlnjgbxtep.supabase.co',googleEnabled:true};
 toast=m=>messages.push(m);navigate=()=>{};restoreLogin=async()=>{onRestore();};
 authApi=async(...args)=>{onRequest(args);return {};};attachSession=async()=>{onAttach();};
 ({start:signInWithGoogle,finish:finishGoogleSignin})`,Object.assign(ctx,{messages,onRestore:()=>restored++,onAttach:()=>attached++,onRequest:a=>{requests.push(a);if(options.exchangeFailure)throw Error('Exchange failed');}}));
 return {api,messages,removed,requests,urls,get restored(){return restored;},get attached(){return attached;}};
}
test('Google authorization requests account selection with PKCE and exact app return URL',async()=>{
 const h=harness();await h.api.start();const url=new URL(h.urls[0]);
 assert.equal(url.searchParams.get('prompt'),'select_account');assert.equal(url.searchParams.get('code_challenge_method'),'s256');
 assert.equal(url.searchParams.get('redirect_to'),'https://example.invalid/MoneyMate-NZ/');assert.equal(url.searchParams.get('code_challenge').length,43);
});
for(const [search,hash] of [['?error=access_denied&error_description=untrusted',''],['','#error=access_denied&error_description=untrusted']]){
 test('OAuth denial cleans callback, restores previous login and does not exchange a code '+hash,async()=>{
  const h=harness(search,hash);await h.api.finish();assert.equal(h.restored,1);assert.equal(h.attached,0);assert.equal(h.requests.length,0);
  assert.equal(h.removed.length,1);assert.equal(h.urls.at(-1),'/MoneyMate-NZ/#account');assert.match(h.messages[0],/cancelled or denied/);assert.ok(!h.messages[0].includes('untrusted'));
 });
}
test('successful callback exchanges PKCE exactly once and clears the verifier',async()=>{
 const h=harness('?code=synthetic-code');await h.api.finish();assert.equal(h.attached,1);assert.equal(h.removed.length,1);
 assert.equal(h.requests[0][0],'/auth/v1/token?grant_type=pkce');assert.equal(h.requests[0][2].code_verifier,'test-verifier');assert.equal(h.urls[0],'/MoneyMate-NZ/');
});

test('expired verifier restores the saved account without exchanging a code',async()=>{
 const h=harness('?code=synthetic-code','',{missingVerifier:true});await h.api.finish();
 assert.equal(h.restored,1);assert.equal(h.requests.length,0);assert.match(h.messages[0],/expired/);
});
test('failed exchange restores the previous saved account',async()=>{
 const h=harness('?code=synthetic-code','',{exchangeFailure:true});await h.api.finish();
 assert.equal(h.restored,1);assert.equal(h.attached,0);assert.equal(h.requests.length,1);assert.equal(h.messages[0],'Exchange failed');
});
