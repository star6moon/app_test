#!/usr/bin/env python3
"""
식물 이름 목록 만들기 (GitHub Actions 의 'Plant names' 워크플로가 실행).

1. Pl@ntNet 이 알아보는 모든 종의 학명과 영어 일반명을 받습니다.
2. 각 학명의 한국어 이름을 Wikidata 에서 찾습니다 (한국어 라벨 / 한국어 일반명 P1843).
3. app/src/main/assets/plant_names.json 으로 저장합니다.
   {"v": 1, "names": {"taraxacum officinale": ["서양민들레", "Common dandelion"], ...}}
   키는 앱의 ScientificName.binomialKey 와 같은 규칙("속명 종소명" 소문자)입니다.

한국어 이름이 없는 종은 앱이 학명을 한글 발음으로 바꿔 보여줍니다 (LatinHangul.kt).
사용법: PLANTNET_API_KEY=... python3 build.py <출력 파일>
"""
import json
import os
import re
import sys
import time
import urllib.error
import urllib.parse
import urllib.request

PLANTNET = "https://my-api.plantnet.org/v2"
WIKIDATA_SPARQL = "https://query.wikidata.org/sparql"
USER_AGENT = "PlantDexNameBuilder/1.0 (https://github.com/star6moon/app_test)"
HANGUL = re.compile(r"[가-힣]")
HYBRID_MARKS = {"×", "x", "X"}


def binomial_key(name: str):
    words = [w for w in name.replace("×", " × ").split() if w not in HYBRID_MARKS]
    if len(words) < 2:
        return None
    epithet = words[1].lower()
    if not re.fullmatch(r"[a-z][a-z-]+", epithet) or words[1][0].isupper():
        return None
    return f"{words[0].lower()} {epithet}"


def http(url, data=None, headers=None, tries=6):
    headers = {"User-Agent": USER_AGENT, **(headers or {})}
    for attempt in range(tries):
        try:
            req = urllib.request.Request(url, data=data, headers=headers)
            with urllib.request.urlopen(req, timeout=120) as res:
                return json.loads(res.read().decode("utf-8"))
        except urllib.error.HTTPError as e:
            body = e.read()[:300].decode("utf-8", "replace")
            if e.code in (429, 500, 502, 503, 504) and attempt < tries - 1:
                wait = int(e.headers.get("Retry-After") or 0) or 5 * (attempt + 1)
                print(f"  HTTP {e.code}, {wait}s 뒤 다시 시도", flush=True)
                time.sleep(wait)
                continue
            raise RuntimeError(f"HTTP {e.code} {url.split('?')[0]}: {body}") from None
        except (urllib.error.URLError, TimeoutError) as e:
            if attempt < tries - 1:
                time.sleep(5 * (attempt + 1))
                continue
            raise


def plantnet_species(key: str):
    """Pl@ntNet 의 종 목록 [(학명, 영어 일반명)] — 'all' 프로젝트가 안 되면 프로젝트별로 모읍니다."""
    def project_species(project):
        url = f"{PLANTNET}/projects/{project}/species?lang=en&api-key={urllib.parse.quote(key)}"
        data = http(url)
        return data if isinstance(data, list) else data.get("species") or data.get("results") or []

    try:
        items = project_species("all")
        print(f"Pl@ntNet 'all': {len(items)}종", flush=True)
    except RuntimeError as e:
        print(f"'all' 목록 실패 ({e}), 프로젝트별로 모읍니다", flush=True)
        projects = http(f"{PLANTNET}/projects?lang=en&api-key={urllib.parse.quote(key)}")
        items = []
        for p in projects:
            pid = p.get("id") if isinstance(p, dict) else p
            try:
                got = project_species(pid)
                print(f"  {pid}: {len(got)}종", flush=True)
                items.extend(got)
            except RuntimeError as err:
                print(f"  {pid}: 실패 {err}", flush=True)

    species = {}
    for it in items:
        name = it.get("scientificNameWithoutAuthor") or it.get("scientificName") or ""
        key_ = binomial_key(name)
        if not key_:
            continue
        common = [c for c in (it.get("commonNames") or []) if isinstance(c, str) and c.strip()]
        prev = species.get(key_)
        if prev is None or (not prev[1] and common):
            species[key_] = (name.strip(), common[0].strip() if common else "")
    return species


def wikidata_korean(names):
    """학명 → 한국어 이름. 한국어 라벨(한글일 때)을 먼저, 없으면 한국어 일반명(P1843)."""
    found = {}
    batch_size = 200
    names = sorted(set(names))
    for i in range(0, len(names), batch_size):
        batch = names[i:i + batch_size]
        values = " ".join(json.dumps(n, ensure_ascii=False) for n in batch)
        query = f"""
SELECT ?name ?label ?common WHERE {{
  VALUES ?name {{ {values} }}
  ?item wdt:P225 ?name .
  OPTIONAL {{ ?item rdfs:label ?label FILTER(LANG(?label) = "ko") }}
  OPTIONAL {{ ?item wdt:P1843 ?common FILTER(LANG(?common) = "ko") }}
}}"""
        data = http(
            WIKIDATA_SPARQL,
            data=urllib.parse.urlencode({"query": query}).encode(),
            headers={"Accept": "application/sparql-results+json", "Content-Type": "application/x-www-form-urlencoded"},
        )
        for row in data["results"]["bindings"]:
            name = row["name"]["value"]
            label = row.get("label", {}).get("value", "")
            common = row.get("common", {}).get("value", "")
            ko = label if HANGUL.search(label) else (common if HANGUL.search(common) else "")
            if ko and name not in found:
                found[name] = ko.strip()
        if (i // batch_size) % 20 == 0:
            print(f"  Wikidata {i + len(batch)}/{len(names)} (한국어 {len(found)})", flush=True)
        time.sleep(1)
    return found


def main():
    out = sys.argv[1]
    key = os.environ["PLANTNET_API_KEY"]
    species = plantnet_species(key)
    print(f"학명 {len(species)}종", flush=True)
    korean = wikidata_korean([name for name, _ in species.values()])

    names = {}
    for k, (name, english) in sorted(species.items()):
        ko = korean.get(name, "")
        if ko or english:
            names[k] = [ko, english]
    with open(out, "w", encoding="utf-8") as f:
        json.dump({"v": 1, "names": names}, f, ensure_ascii=False, separators=(",", ":"))
    with_ko = sum(1 for v in names.values() if v[0])
    with_en = sum(1 for v in names.values() if v[1])
    print(f"저장: {len(names)}종 (한국어 {with_ko}, 영어 {with_en}) → {out}", flush=True)
    if with_ko == 0:
        sys.exit("한국어 이름을 하나도 찾지 못했습니다")


if __name__ == "__main__":
    main()
