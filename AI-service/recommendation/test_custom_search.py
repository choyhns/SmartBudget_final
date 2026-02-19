"""
Custom Search API 직접 테스트 (원인 확인용)
.env를 읽어서 한 번만 호출하고 Google 응답을 그대로 출력합니다.
실행: cd recommandation && python test_custom_search.py
"""
import os
import json
import urllib.request
import urllib.parse
from dotenv import load_dotenv

_this_dir = os.path.dirname(os.path.abspath(__file__))
load_dotenv(os.path.join(_this_dir, ".env"))
load_dotenv(os.path.join(_this_dir, "..", "backend", "smartbudget", ".env"))

key = os.getenv("GOOGLE_CUSTOM_SEARCH_API_KEY") or os.getenv("GOOGLE_CUSTOMSEARCH_API_KEY")
cx = os.getenv("GOOGLE_CUSTOM_SEARCH_CX") or os.getenv("GOOGLE_CUSTOMSEARCH_CX")
url_base = "https://customsearch.googleapis.com/customsearch/v1"

if not key or not cx:
    print("GOOGLE_CUSTOM_SEARCH_API_KEY 또는 GOOGLE_CUSTOM_SEARCH_CX 가 .env에 없습니다.")
    exit(1)

# 키 앞뒤만 표시 (보안)
print("API 키 (일부):", key[:10] + "..." + key[-4:] if len(key) > 14 else "(짧음)")
print("CX:", cx)
print()

q = "테스트"
encoded = urllib.parse.quote(q, encoding="utf-8")
url = f"{url_base}?key={key}&cx={cx}&q={encoded}&num=2"
req = urllib.request.Request(url, headers={"User-Agent": "SmartBudget-Test/1.0"})

try:
    with urllib.request.urlopen(req, timeout=10) as resp:
        body = resp.read().decode("utf-8")
    print("상태: 200 OK")
    data = json.loads(body)
    items = data.get("items") or []
    print("검색 결과 수:", len(items))
    for i, item in enumerate(items[:3], 1):
        print(f"  {i}. {item.get('title', '')[:60]}")
except urllib.error.HTTPError as e:
    body = e.read().decode("utf-8", errors="replace")
    print("상태:", e.code, e.reason)
    print("응답 본문:")
    try:
        print(json.dumps(json.loads(body), indent=2, ensure_ascii=False))
    except Exception:
        print(body[:1000])
except Exception as e:
    print("오류:", type(e).__name__, e)
