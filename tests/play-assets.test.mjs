import test from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';

// Google's store listing contract: RGBA icon, RGB feature graphic, fixed sizes.
// https://support.google.com/googleplay/android-developer/answer/9866151
test('Play artwork meets required PNG dimensions, colour formats and icon size limit',()=>{
 for(const [file,width,height,colour] of [
  ['play-icon-512.png',512,512,6],['feature-graphic-1024x500.png',1024,500,2]
 ]) {
  const png=readFileSync(new URL('../store-assets/'+file,import.meta.url));
  assert.equal(png.subarray(0,8).toString('hex'),'89504e470d0a1a0a');
  assert.equal(png.subarray(12,16).toString(),'IHDR');
  assert.equal(png.readUInt32BE(16),width);assert.equal(png.readUInt32BE(20),height);
  assert.equal(png[24],8);assert.equal(png[25],colour);
  if(file.startsWith('play-icon'))assert.ok(png.length<=1024*1024);
 }
});
