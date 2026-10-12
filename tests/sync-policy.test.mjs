import test from "node:test";
import assert from "node:assert/strict";
import {decideSync} from "../docs/sync-preview/sync-policy.mjs";

test("cloud sync requires explicit consent before any upload or download", () => {
  assert.equal(decideSync({enabled:false,baseRevision:0,baseDigest:"old",localDigest:"new",remoteRevision:0}),"needs-consent");
  assert.equal(decideSync({enabled:false,baseRevision:0,baseDigest:"old",localDigest:"new",remoteRevision:19}),"needs-consent");
});
test("unchanged cloud and same local data cause no destructive action", () => {
  assert.equal(decideSync({enabled:true,baseRevision:5,baseDigest:"same",localDigest:"same",remoteRevision:5}),"unchanged");
});
test("local changes upload only when server revision is unchanged", () => {
  assert.equal(decideSync({enabled:true,baseRevision:5,baseDigest:"old",localDigest:"edited",remoteRevision:5}),"upload");
});
test("cloud changes are safely downloaded only if local data was not edited", () => {
  assert.equal(decideSync({enabled:true,baseRevision:5,baseDigest:"same",localDigest:"same",remoteRevision:6}),"download");
  assert.equal(decideSync({enabled:true,baseRevision:0,baseDigest:"empty",localDigest:"empty",remoteRevision:6,localEmpty:true}),"download");
});
test("simultaneous edits on two devices require conflict resolution", () => {
  assert.equal(decideSync({enabled:true,baseRevision:5,baseDigest:"old",localDigest:"edited on phone",remoteRevision:6}),"conflict");
  assert.equal(decideSync({enabled:true,baseRevision:0,baseDigest:"initial",localDigest:"laptop edits",remoteRevision:1}),"conflict");
});
test("invalid server revisions fail closed", () => {
  for (const value of [-1,1.25,NaN,Infinity,null]) {
    assert.throws(() => decideSync({enabled:true,baseRevision:0,baseDigest:"x",localDigest:"y",remoteRevision:value}));
  }
});
