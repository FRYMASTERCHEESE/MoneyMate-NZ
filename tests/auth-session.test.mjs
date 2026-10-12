import {test} from "node:test";
import assert from "node:assert/strict";
import {normalizeLoginSession,sessionInvalidated} from "../docs/auth-session.mjs";

const id="11111111-1111-4111-8111-111111111111";
function session(){return {access_token:"jwt-test",refresh_token:"refresh-test",expires_in:3600,user:{id,email:"person@example.invalid"},ignored_secret:"should not persist"};}
test("browser session is reduced to essential fields, not accidental data",()=>{
 const result=normalizeLoginSession(session());
 assert.equal(result.user.id,id);
 assert.equal(result.user.email,"person@example.invalid");
 assert.ok(result.expires_at>Math.floor(Date.now()/1000));
 assert.deepEqual(Object.keys(result).sort(),["access_token","expires_at","refresh_token","user"]);
 assert.ok(!JSON.stringify(result).includes("should not persist"));
});
test("validated owner id must match the saved session",()=>{
 assert.throws(()=>normalizeLoginSession(session(),"22222222-2222-4222-8222-222222222222"),/identity changed/);
 assert.equal(normalizeLoginSession(session(),id).user.id,id);
});
test("malformed or incomplete sessions cannot be restored",()=>{
 for (const data of [null,{},[],{user:{id},access_token:"x"}, {...session(),user:{id:"evil"}}])assert.throws(()=>normalizeLoginSession(data));
});
test("a numeric expiry supplied by the server survives normalisation",()=>{
 const data={...session(),expires_at:2000000000};
 assert.equal(normalizeLoginSession(data).expires_at,2000000000);
});
test("definitive auth failure is different from offline/network failures",()=>{
 for(const code of [400,401,403])assert.equal(sessionInvalidated(code),true);
 for(const code of [429,0,408,500,503,undefined])assert.equal(sessionInvalidated(code),false);
});
