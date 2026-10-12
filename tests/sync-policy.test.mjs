import test from "node:test";
import assert from "node:assert/strict";
import {decideSync} from "../docs/sync-policy.mjs";
test("Never overwrite someone else's recent changes",()=>{
 assert.equal(decideSync({enabled:true,baseRevision:2,baseDigest:"old",localDigest:"edited",remoteRevision:3}),"conflict");
});
test("Download changed cloud when local is unchanged",()=>{
 assert.equal(decideSync({enabled:true,baseRevision:2,baseDigest:"same",localDigest:"same",remoteRevision:3}),"download");
});
test("Upload local changes only with matching base",()=>{
 assert.equal(decideSync({enabled:true,baseRevision:2,baseDigest:"old",localDigest:"new",remoteRevision:2}),"upload");
 assert.equal(decideSync({enabled:true,baseRevision:2,baseDigest:"same",localDigest:"same",remoteRevision:2}),"unchanged");
});
test("Never activate remote storage without explicit choice",()=>{
 assert.equal(decideSync({enabled:false,baseRevision:0,baseDigest:"",localDigest:"anything",remoteRevision:0}),"needs-consent");
});
