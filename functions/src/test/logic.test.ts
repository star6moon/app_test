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
