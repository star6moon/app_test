/**
 * 앱(Kotlin)의 PlantCatalog / ScientificName / SpeciesKey 와 같은 규칙으로 종을 판정합니다.
 * 서버에서 "같은 종을 10m 안에 또 등록"하는지 확인할 때 앱과 같은 결과가 나와야 합니다.
 */
import catalogData from "./catalogs.json";

interface CatalogSpecies {
  id: string;
  scientificName: string;
  synonyms?: string[];
  acceptGenus?: boolean;
}

const HYBRID_MARKS = new Set(["×", "x", "X"]);

function words(name: string): string[] {
  return name
    .replace(/×/g, " × ")
    .trim()
    .split(/\s+/)
    .filter((w) => w.length > 0 && !HYBRID_MARKS.has(w));
}

/** "Prunus × yedoensis Matsum." → "prunus yedoensis". 종소명이 없으면 null */
export function binomialKey(name: string): string | null {
  const w = words(name);
  if (w.length < 2) return null;
  const epithet = w[1].toLowerCase();
  if (!/^[a-z][a-z-]+$/.test(epithet) || w[1][0] !== w[1][0].toLowerCase()) return null;
  return `${w[0].toLowerCase()} ${epithet}`;
}

export function genusKey(name: string): string | null {
  const w = words(name);
  return w.length > 0 ? w[0].toLowerCase() : null;
}

const species = (catalogData as { species: CatalogSpecies[] }).species;
const byBinomial = new Map<string, CatalogSpecies>();
const byGenus = new Map<string, CatalogSpecies>();
for (const s of species) {
  for (const n of [s.scientificName, ...(s.synonyms ?? [])]) {
    const key = binomialKey(n);
    if (key && !byBinomial.has(key)) byBinomial.set(key, s);
  }
  if (s.acceptGenus) {
    const g = genusKey(s.scientificName);
    if (g && !byGenus.has(g)) byGenus.set(g, s);
  }
}

export function matchCatalog(scientificName: string): CatalogSpecies | null {
  const b = binomialKey(scientificName);
  if (b && byBinomial.has(b)) return byBinomial.get(b)!;
  const g = genusKey(scientificName);
  return (g && byGenus.get(g)) || null;
}

/** 같은 종을 하나로 셀 때 쓰는 키 (도감 종 id → 속+종소명 → 학명 소문자) */
export function speciesKey(scientificName: string): string {
  return matchCatalog(scientificName)?.id ?? binomialKey(scientificName) ?? scientificName.toLowerCase();
}

export function isCatalogSpecies(scientificName: string): boolean {
  return matchCatalog(scientificName) !== null;
}
