import test from "node:test";
import assert from "node:assert/strict";
import {readFileSync} from "node:fs";
import {strictData,fortnightPlan} from "../docs/web-core.mjs";

// This exact asset is also consumed by FortnightPlanTest on debug and R8 APKs.
const fixtures=JSON.parse(readFileSync(new URL(
  "../app/src/androidTest/assets/fortnight-plan-cases.json",import.meta.url),"utf8"));
for(const fixture of fixtures) {
  test(`shared Android/web forecast: ${fixture.name}`,()=>{
    const data=strictData(fixture.data);
    const before=structuredClone(data);
    if(fixture.error) {
      assert.throws(()=>fortnightPlan(data,fixture.start,fixture.days),new RegExp(fixture.error));
    }else{
      assert.deepEqual(fortnightPlan(data,fixture.start,fixture.days),fixture.expected);
    }
    assert.deepEqual(data,before,"Forecast must preserve transactions, goals, budgets, bills and schedules");
  });
}
