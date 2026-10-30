#!/usr/bin/env python3
"""
식물 이름 목록 만들기 (GitHub Actions 의 'Plant names' 워크플로가 실행).

1. Pl@ntNet 이 알아보는 모든 종의 학명과 영어 일반명을 받습니다.
2. 각 학명의 한국어 이름을 국립수목원 국가표준식물목록(공공데이터포털 KpniService, 비밀 KPNI_SERVICE_KEY)에서 가장 먼저 찾고,
   없으면 Wikidata 에서 찾습니다 (한국어 라벨 / 한국어 일반명 P1843).
   Wikidata 에 없으면 GBIF 에서 찾습니다: 한국에서 관찰된 식물 종 목록을 받아 각 종의 한국어 일반명을 모읍니다.
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
GBIF = "https://api.gbif.org/v1"
KPNI = "https://apis.data.go.kr/1400119/KpniService/scnmSearch"
USER_AGENT = "PlantDexNameBuilder/1.0 (https://github.com/star6moon/app_test)"
HANGUL = re.compile(r"[가-힣]")
HYBRID_MARKS = {"×", "x", "X"}

# 데이터베이스에서 잘 안 잡히는 흔한 식물 (학명 표기가 여러 가지인 원예종 등). 키는 binomial key.
OVERRIDES = {
    "chrysanthemum morifolium": "국화",
    "chrysanthemum grandiflorum": "국화",
    "dendranthema grandiflorum": "국화",
    "dendranthema morifolium": "국화",
    "bellis perennis": "데이지",
}


def binomial_key(name: str):
    words = [w for w in name.replace("×", " × ").split() if w not in HYBRID_MARKS]
    if len(words) < 2:
        return None
    epithet = words[1].lower()
    if not re.fullmatch(r"[a-z][a-z-]+", epithet) or words[1][0].isupper():
        return None
    return f"{words[0].lower()} {epithet}"


def http(url, data=None, headers=None, tries=6, timeout=120):
    headers = {"User-Agent": USER_AGENT, **(headers or {})}
    for attempt in range(tries):
        try:
            req = urllib.request.Request(url, data=data, headers=headers)
            with urllib.request.urlopen(req, timeout=timeout) as res:
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
    """Pl@ntNet 의 종 목록 {binomial: (학명, 영어 일반명)}.
    전체 목록은 한 번에 받으면 시간이 너무 걸려서 쪽(page) 단위로 받고, 안 되면 지역 프로젝트별로 모읍니다."""
    k = urllib.parse.quote(key)
    items = []

    def as_list(data):
        if isinstance(data, list):
            return data
        if isinstance(data, dict):
            for field in ("species", "results", "data", "items"):
                if isinstance(data.get(field), list):
                    return data[field]
        return []

    # 1) 쪽 단위 전체 목록
    try:
        page, size = 1, 1000
        while True:
            data = http(f"{PLANTNET}/species?lang=en&pageSize={size}&page={page}&api-key={k}", tries=3, timeout=180)
            got = as_list(data)
            if page == 1:
                print(f"/v2/species 응답 형태: {type(data).__name__}, 첫 쪽 {len(got)}종", flush=True)
            items.extend(got)
            if len(got) < size or page > 400:
                break
            if page == 1 and len(got) > size:  # 쪽 나누기를 무시하고 전부 준 경우
                break
            page += 1
        print(f"Pl@ntNet 전체 목록: {len(items)}종", flush=True)
    except RuntimeError as e:
        print(f"전체 목록 실패: {e}", flush=True)
        items = []

    # 2) 지역 프로젝트별 (전체 목록이 비었을 때)
    if not items:
        projects = as_list(http(f"{PLANTNET}/projects?lang=en&api-key={k}", tries=3, timeout=120))
        print(f"프로젝트 {len(projects)}개", flush=True)
        for p in projects:
            pid = p.get("id") if isinstance(p, dict) else p
            if pid in ("all", "k-world-flora"):
                continue
            try:
                got = as_list(http(f"{PLANTNET}/projects/{pid}/species?lang=en&api-key={k}", tries=2, timeout=300))
                print(f"  {pid}: {len(got)}종", flush=True)
                items.extend(got)
            except (RuntimeError, urllib.error.URLError, TimeoutError) as err:
                print(f"  {pid}: 실패 {err}", flush=True)

    species = {}
    for it in items:
        if not isinstance(it, dict):
            continue
        name = it.get("scientificNameWithoutAuthor") or it.get("scientificName") or it.get("name") or ""
        key_ = binomial_key(name)
        if not key_:
            continue
        common = [c for c in (it.get("commonNames") or []) if isinstance(c, str) and c.strip()]
        prev = species.get(key_)
        if prev is None or (not prev[1] and common):
            species[key_] = (name.strip(), common[0].strip() if common else "")
    return species


def wikidata_korean(names):
    """학명 → 한국어 이름 {binomial key: 이름}. 한국어 라벨(한글일 때)을 먼저, 없으면 한국어 일반명(P1843).
    - 교잡종은 Wikidata 에 'Chrysanthemum × morifolium' 처럼 적혀 있어 '×' 를 넣은 이름도 함께 찾습니다.
    - 옛 학명(이명)이면 그 이명을 가진 현재 학명 항목(P1420)의 이름을 씁니다."""
    direct, via_synonym = {}, {}
    batch_size = 120
    names = sorted(set(names))
    for i in range(0, len(names), batch_size):
        batch = names[i:i + batch_size]
        variants = set(batch)
        for n in batch:
            w = n.split()
            if len(w) >= 2 and "×" not in n:
                variants.add(f"{w[0]} × {' '.join(w[1:])}")
        values = " ".join(json.dumps(n, ensure_ascii=False) for n in sorted(variants))
        query = f"""
SELECT ?name ?via ?label ?common WHERE {{
  VALUES ?name {{ {values} }}
  {{ ?item wdt:P225 ?name . BIND("direct" AS ?via) }}
  UNION
  {{ ?syn wdt:P225 ?name . ?item wdt:P1420 ?syn . BIND("synonym" AS ?via) }}
  OPTIONAL {{ ?item rdfs:label ?label FILTER(LANG(?label) = "ko") }}
  OPTIONAL {{ ?item wdt:P1843 ?common FILTER(LANG(?common) = "ko") }}
}}"""
        data = http(
            WIKIDATA_SPARQL,
            data=urllib.parse.urlencode({"query": query}).encode(),
            headers={"Accept": "application/sparql-results+json", "Content-Type": "application/x-www-form-urlencoded"},
        )
        for row in data["results"]["bindings"]:
            key = binomial_key(row["name"]["value"])
            if not key:
                continue
            label = row.get("label", {}).get("value", "")
            common = row.get("common", {}).get("value", "")
            ko = label if HANGUL.search(label) else (common if HANGUL.search(common) else "")
            if not ko:
                continue
            target = direct if row["via"]["value"] == "direct" else via_synonym
            target.setdefault(key, ko.strip())
        if (i // batch_size) % 40 == 0:
            print(f"  Wikidata {i + len(batch)}/{len(names)} (한국어 {len(direct)}, 이명으로 {len(via_synonym)})", flush=True)
        time.sleep(1)
    return {**via_synonym, **direct}


def kpni_names(service_key: str):
    """국가표준식물목록의 학명(정명·이명) → 추천 국명. {binomial key: 국명}
    같은 종에 변종·품종 이름이 여럿이면 종 수준 학명의 국명을, 정명을 이명보다 먼저 씁니다."""
    import xml.etree.ElementTree as ET

    rank_of = lambda n: 1 if re.search(r"\s(var|subsp|ssp|f|forma)\.\s", n) else 0
    best = {}  # key → (우선순위, 국명)
    page, size, total = 1, 1000, None
    while total is None or (page - 1) * size < total:
        url = f"{KPNI}?serviceKey={urllib.parse.quote(service_key, safe='')}&pageNo={page}&numOfRows={size}"
        for attempt in range(5):
            try:
                req = urllib.request.Request(url, headers={"User-Agent": USER_AGENT})
                with urllib.request.urlopen(req, timeout=180) as res:
                    root = ET.fromstring(res.read())
                break
            except Exception as e:
                if attempt == 4:
                    raise
                print(f"  국가표준식물목록 {page}쪽 재시도 ({e})", flush=True)
                time.sleep(10 * (attempt + 1))
        code = root.findtext("header/resultCode")
        if code not in ("00", None):
            raise RuntimeError(f"국가표준식물목록 오류 {code}: {root.findtext('header/resultMsg')}")
        total = int(root.findtext("body/totalCount") or 0)
        for item in root.iter("item"):
            name = (item.findtext("plantSpecsScnm") or "").strip()
            ko = (item.findtext("plantGnrlNm") or "").strip()
            key = binomial_key(name)
            if not key or not HANGUL.search(ko):
                continue
            accepted = (item.findtext("stpltScnmRltnCdNm") or "").strip() == "정명"
            priority = (rank_of(name), 0 if accepted else 1)
            if key not in best or priority < best[key][0]:
                best[key] = (priority, ko)
        if page == 1 or page % 10 == 0:
            print(f"  국가표준식물목록 {min(page * size, total)}/{total} (종 {len(best)})", flush=True)
        page += 1
        time.sleep(0.5)
    print(f"국가표준식물목록 국명 {len(best)}종", flush=True)
    return {k: ko for k, (_, ko) in best.items()}


def gbif_korea_names():
    """한국에서 관찰 기록이 있는 식물 종 → 한국어 일반명 (GBIF). {학명: 한국어 이름}"""
    from collections import Counter
    from concurrent.futures import ThreadPoolExecutor

    keys = []
    offset, size = 0, 1000
    while True:
        data = http(
            f"{GBIF}/occurrence/search?country=KR&kingdomKey=6&limit=0&facet=speciesKey"
            f"&facetLimit={size}&facetOffset={offset}",
            tries=4, timeout=120,
        )
        counts = (data.get("facets") or [{}])[0].get("counts") or []
        keys.extend(c["name"] for c in counts)
        if len(counts) < size:
            break
        offset += size
    print(f"GBIF: 한국에서 관찰된 식물 {len(keys)}종", flush=True)

    def one(key):
        try:
            sp = http(f"{GBIF}/species/{key}", tries=3, timeout=30)
            vn = http(f"{GBIF}/species/{key}/vernacularNames?limit=300", tries=3, timeout=30)
        except Exception as e:  # 한 종이 실패해도 나머지는 계속
            return None, None
        names = [
            v["vernacularName"].strip()
            for v in vn.get("results", [])
            if v.get("language") == "kor" and HANGUL.search(v.get("vernacularName") or "")
        ]
        if not names:
            return sp.get("canonicalName"), None
        # 여러 출처가 같은 이름을 쓰면 그 이름, 아니면 처음 이름
        best = Counter(names).most_common(1)[0][0]
        return sp.get("canonicalName"), best

    found = {}
    with ThreadPoolExecutor(max_workers=8) as pool:
        for i, (name, ko) in enumerate(pool.map(one, keys)):
            if name and ko:
                found[name] = ko
            if i % 2000 == 0:
                print(f"  GBIF {i}/{len(keys)} (한국어 {len(found)})", flush=True)
    print(f"GBIF 한국어 이름 {len(found)}종", flush=True)
    return found


def main():
    out = sys.argv[1]
    key = os.environ["PLANTNET_API_KEY"]
    species = plantnet_species(key)
    print(f"학명 {len(species)}종", flush=True)
    if not species:
        sys.exit("Pl@ntNet 종 목록을 받지 못했습니다")
    kpni = {}
    if os.environ.get("KPNI_SERVICE_KEY"):
        try:
            kpni = kpni_names(os.environ["KPNI_SERVICE_KEY"])
        except Exception as e:
            print(f"국가표준식물목록 단계 실패: {e}", flush=True)
    korean = wikidata_korean([name for name, _ in species.values()])
    try:
        gbif = gbif_korea_names()
    except Exception as e:
        print(f"GBIF 단계 실패: {e}", flush=True)
        gbif = {}
    gbif_by_key = {binomial_key(n): ko for n, ko in gbif.items() if binomial_key(n)}

    names = {}
    # 이름이 없는 종도 학명으로는 검색되도록 Pl@ntNet 이 아는 모든 종을 넣습니다.
    for k, (name, english) in sorted(species.items()):
        ko = OVERRIDES.get(k) or kpni.get(k) or korean.get(k, "") or gbif_by_key.get(k, "")
        names[k] = [ko, english]
    # Pl@ntNet 목록에 이름이 다르게 올라 있어도 찾을 수 있도록 GBIF 의 한국 식물도 넣습니다.
    for k, ko in {**gbif_by_key, **kpni, **OVERRIDES}.items():
        if k in names:
            continue
        names[k] = [ko, ""]
    with open(out, "w", encoding="utf-8") as f:
        json.dump({"v": 1, "names": names}, f, ensure_ascii=False, separators=(",", ":"))
    with_ko = sum(1 for v in names.values() if v[0])
    with_en = sum(1 for v in names.values() if v[1])
    print(f"저장: {len(names)}종 (한국어 {with_ko}, 영어 {with_en}) → {out}", flush=True)
    if with_ko == 0:
        sys.exit("한국어 이름을 하나도 찾지 못했습니다")


if __name__ == "__main__":
    main()
