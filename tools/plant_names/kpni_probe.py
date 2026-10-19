#!/usr/bin/env python3
"""국가표준식물목록 조회 서비스(공공데이터포털 15142872)의 명세를 확인하고 시험 호출합니다 (Deploy → kpni-probe)."""
import html
import json
import os
import re
import urllib.parse
import urllib.request

KEY = os.environ.get("KPNI_SERVICE_KEY", "")
UA = {"User-Agent": "Mozilla/5.0 PlantDexNameBuilder"}


def get(url, timeout=60):
    req = urllib.request.Request(url, headers=UA)
    try:
        with urllib.request.urlopen(req, timeout=timeout) as res:
            return res.status, res.read().decode("utf-8", "replace")
    except urllib.error.HTTPError as e:
        return e.code, e.read().decode("utf-8", "replace")


status, page = get("https://www.data.go.kr/data/15142872/openapi.do")
print("명세 페이지:", status, len(page))
urls = sorted(set(re.findall(r"https?://apis\.data\.go\.kr/[A-Za-z0-9_/.-]+", page)))
print("API 주소:", urls)
# 스웨거(명세) JSON 이 페이지 안에 들어 있으면 경로와 변수만 뽑아 봅니다.
m = re.search(r"swaggerJson\s*=\s*`(.*?)`", page, re.S) or re.search(r"swaggerJson\s*=\s*'(.*?)';", page, re.S)
if m:
    try:
        spec = json.loads(m.group(1))
        print("host/basePath:", spec.get("host"), spec.get("basePath"))
        for path, ops in spec.get("paths", {}).items():
            for method, op in ops.items():
                params = [(p.get("name"), p.get("required"), p.get("description")) for p in op.get("parameters", [])]
                print("PATH", method.upper(), path, op.get("summary"), params)
    except Exception as e:
        print("스웨거 해석 실패", e, m.group(1)[:500])
else:
    text = re.sub(r"<[^>]+>", " ", page)
    text = html.unescape(re.sub(r"\s+", " ", text))
    for kw in ["요청주소", "서비스URL", "End Point", "상세기능", "요청변수", "출력결과", "학명", "국명"]:
        for mm in re.finditer(kw, text):
            print(f"[{kw}]", text[max(0, mm.start() - 80): mm.start() + 400])
            break

if not KEY:
    raise SystemExit("KPNI_SERVICE_KEY 비밀이 없습니다")

for base in urls:
    for extra in ["", "&pageNo=1&numOfRows=3", "&pageNo=1&numOfRows=3&type=json", "&pageNo=1&numOfRows=3&_type=json"]:
        q = f"serviceKey={urllib.parse.quote(KEY, safe='')}{extra}"
        for name_param in ["", "&searchWrd=%EA%B5%AD%ED%99%94", "&st=1&sw=Chrysanthemum"]:
            status, body = get(f"{base}?{q}{name_param}", timeout=30)
            print(f"== {base} {extra} {name_param}: HTTP {status}")
            print(body[:1200].replace(KEY, "***"))
