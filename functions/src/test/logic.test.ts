import { test } from "node:test";
import assert from "node:assert/strict";
import { binomialKey, speciesKey } from "../catalog/species";
import { distanceMeters, geohash } from "../geo";

test("binomial key matches the app's normalization", () => {
  assert.equal(binomialKey("Prunus × yedoensis Matsum."), "prunus yedoensis");
  assert.equal(binomialKey("Petunia x atkinsiana"), "petunia atkinsiana");
  assert.equal(binomialKey("Rhododendron yedoense var. poukhanense"), "rhododendron yedoense");
  assert.equal(binomialKey("Capsella bursa-pastoris (L.) Medik."), "capsella bursa-pastoris");
  assert.equal(binomialKey("Taraxacum"), null);
  assert.equal(binomialKey("Rosa L."), null);
});

test("species key uses catalog ids, synonyms and genus-wide species", () => {
  assert.equal(speciesKey("Prunus serrulata Lindl."), "cherry");
  assert.equal(speciesKey("Prunus × yedoensis"), "cherry");
  assert.equal(speciesKey("Forsythia ovata"), "forsythia");
  assert.equal(speciesKey("Viola × wittrockiana"), "pansy");
  assert.equal(speciesKey("Linnaea × grandiflora"), "abelia");
  assert.equal(speciesKey("Quercus acutissima"), "quercus acutissima");
});

test("distance and geohash", () => {
  assert.ok(Math.abs(distanceMeters(37.5665, 126.978, 37.5675, 126.978) - 111.2) < 0.5);
  // 알려진 값: 서울시청 근처
  assert.equal(geohash(37.5665, 126.978, 6), "wydm9q");
  assert.equal(geohash(57.64911, 10.40744, 11), "u4pruydqqvj");
});

import { decideAction, suspensionDays, ReportInfo } from "../moderationPolicy";

const r = (reporterId: string, reasonCode: ReportInfo["reasonCode"], createdAtMillis = 1000): ReportInfo =>
  ({ reporterId, reasonCode, createdAtMillis });

test("wrong info from two people asks the owner to correct the entry", () => {
  assert.equal(decideAction("entry", [r("a", "wrong_info")], "owner").action, "none");
  assert.equal(decideAction("entry", [r("a", "wrong_info"), r("a", "wrong_info")], "owner").action, "none");
  const two = [r("a", "wrong_info"), r("b", "wrong_info")];
  assert.deepEqual(decideAction("entry", two, "owner"), { action: "needs_correction", strike: false, reason: "wrong_info" });
  // 고친 뒤에는 그 이후 신고만 셉니다.
  assert.equal(decideAction("entry", two, "owner", 5000).action, "none");
  assert.equal(decideAction("entry", [...two, r("c", "wrong_info", 6000), r("d", "wrong_info", 7000)], "owner", 5000).action, "needs_correction");
});

test("violations hide content and give a strike; the owner's own report is ignored", () => {
  const reports = [r("a", "abuse"), r("b", "spam"), r("owner", "inappropriate_photo")];
  assert.equal(decideAction("comment", reports, "owner").action, "none");
  const three = [...reports, r("c", "abuse")];
  assert.deepEqual(decideAction("comment", three, "owner"), { action: "hide", strike: true, reason: "abuse" });
  assert.deepEqual(decideAction("user", three, "owner"), { action: "none", strike: true, reason: "abuse" });
});

test("not-real photos are hidden without a strike, and wrong info alone never hides", () => {
  const notReal = [r("a", "not_real_photo"), r("b", "not_real_photo"), r("c", "other")];
  assert.deepEqual(decideAction("entry", notReal, "owner"), { action: "hide", strike: false, reason: "not_real_photo" });
  const wrong = ["a", "b", "c", "d"].map((id) => r(id, "wrong_info"));
  assert.equal(decideAction("entry", wrong, "owner").action, "needs_correction");
  // 대상에 맞지 않는 사유는 무시 (댓글에 '정보 불일치')
  assert.equal(decideAction("comment", wrong, "owner").action, "none");
});

test("suspensions get longer", () => {
  assert.deepEqual([1, 2, 3, 4, 9].map(suspensionDays), [3, 7, 30, 30, 30]);
});
