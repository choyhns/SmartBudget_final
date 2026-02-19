# -*- coding: utf-8 -*-
"""shinhancard_check_link.json의 link를 shinhancard_check_cards.json의 card_name에 맞춰 병합"""

import json
import re

def normalize_name(s):
    if not s:
        return ""
    s = re.sub(r"\s+", " ", s).strip()
    s = re.sub(r"\s+\(", "(", s)
    return s

def main():
    base = "d:/work/final-project/smartbudget"
    with open(f"{base}/shinhancard_check_link.json", "r", encoding="utf-8") as f:
        link_list = json.load(f)
    with open(f"{base}/shinhancard_check_cards.json", "r", encoding="utf-8") as f:
        check_cards = json.load(f)

    link_by_name = {}
    link_by_normalized = {}
    for item in link_list:
        name = item.get("name", "").strip()
        link = item.get("link", "").strip()
        if not name or not link:
            continue
        link_by_name[name] = link
        n = normalize_name(name)
        if n and n not in link_by_normalized:
            link_by_normalized[n] = link

    # 카드명 표기 차이 수동 매핑 (cards 기준 -> link의 name)
    manual = {
        "Point Plan 체크(SOL 모임)": "신한카드 Point Plan 체크 SOL",
        "신한카드 처음 체크 마이멜로디": "신한카드 처음 체크 (마이멜로디)",
        "신한카드 Hey Young 체크 포차코": "신한카드 Hey Young 체크(포차코)",
        "신한카드Point Plan체크(서울시다둥이행복카드)": "신한카드 Point Plan 체크(서울시다둥이행복카드)",
        "공무원연금복지 신한카드 S-Choice(선택형) 체크": "공무원연금복지 신한카드 S-Choice (선택형) 체크",
        "나주사랑 신한카드 S-Choice (선택형) 체크": "나주사랑 신한카드 S-Choice(선택형) 체크",
        "신한 후불 기후동행 체크카드": "후불 기후동행카드 서비스 제공온라인 5% 할인오프라인 5% 할인",
    }

    merged = []
    matched = 0
    unmatched = []
    for card in check_cards:
        name = card.get("card_name", "").strip()
        link = link_by_name.get(name)
        if not link:
            link = link_by_normalized.get(normalize_name(name))
        if not link and name in manual:
            link = link_by_name.get(manual[name]) or link_by_normalized.get(normalize_name(manual[name]))
        if not link:
            # 괄호 제거 후 비교 시도: "처음 체크 마이멜로디" vs "처음 체크(마이멜로디)"
            no_paren = re.sub(r"\s*\([^)]*\)", "", name)
            no_paren = re.sub(r"\s+", " ", no_paren).strip()
            for link_name, link_url in link_by_name.items():
                link_no_paren = re.sub(r"\s*\([^)]*\)", "", link_name)
                link_no_paren = re.sub(r"\s+", " ", link_no_paren).strip()
                if no_paren == link_no_paren:
                    link = link_url
                    break

        new_card = {**card}
        if link:
            new_card["link"] = link
            matched += 1
        else:
            unmatched.append(name)
        merged.append(new_card)

    with open(f"{base}/shinhancard_check_cards.json", "w", encoding="utf-8") as f:
        json.dump(merged, f, ensure_ascii=False, indent=2)

    print(f"병합 완료: {len(check_cards)}건 중 {matched}건 링크 매칭")
    if unmatched:
        print(f"매칭 안 됨 ({len(unmatched)}건):")
        for u in unmatched:
            print(f"  - {u}")

if __name__ == "__main__":
    main()
