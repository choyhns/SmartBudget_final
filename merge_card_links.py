# -*- coding: utf-8 -*-
"""shinhancard_cards.json의 link를 shinhancard_credit_cards.json의 card_name에 맞춰 병합"""

import json
import re

def normalize_name(s):
    if not s:
        return ""
    # 공백 정규화: 연속 공백을 하나로, trim
    s = re.sub(r"\s+", " ", s).strip()
    # 괄호 앞 공백 제거: "Point Plan (산리오" -> "Point Plan(산리오"
    s = re.sub(r"\s+\(", "(", s)
    return s

def main():
    base = "d:/work/final-project/smartbudget"
    with open(f"{base}/shinhancard_cards.json", "r", encoding="utf-8") as f:
        cards_with_links = json.load(f)
    with open(f"{base}/shinhancard_credit_cards.json", "r", encoding="utf-8") as f:
        credit_cards = json.load(f)

    # name -> link (정확한 이름 + 정규화 이름 둘 다 등록)
    link_by_name = {}
    link_by_normalized = {}
    for item in cards_with_links:
        name = item.get("name", "").strip()
        link = item.get("link", "").strip()
        if not name or not link:
            continue
        link_by_name[name] = link
        n = normalize_name(name)
        if n and n not in link_by_normalized:
            link_by_normalized[n] = link

    merged = []
    matched = 0
    unmatched = []
    for card in credit_cards:
        name = card.get("card_name", "").strip()
        link = link_by_name.get(name)
        if not link:
            link = link_by_normalized.get(normalize_name(name))
        if not link and "BigPlus" in name:
            # credit에는 BigPlus, cards에는 "Big Plus"
            alt = name.replace("BigPlus", "Big Plus")
            link = link_by_name.get(alt) or link_by_normalized.get(normalize_name(alt))
        if not link and "Hi-Point" in name and "Hi-point" not in name:
            alt = name.replace("Hi-Point", "Hi-point")
            link = link_by_name.get(alt) or link_by_normalized.get(normalize_name(alt))
        if not link and "Hi-Point" in name:
            alt = name.replace("Hi-Point", "Hi-point")
            link = link_by_name.get(alt) or link_by_normalized.get(normalize_name(alt))

        new_card = {**card}
        if link:
            new_card["link"] = link
            matched += 1
        else:
            unmatched.append(name)
        merged.append(new_card)

    with open(f"{base}/shinhancard_credit_cards.json", "w", encoding="utf-8") as f:
        json.dump(merged, f, ensure_ascii=False, indent=2)

    print(f"병합 완료: {len(credit_cards)}건 중 {matched}건 링크 매칭")
    if unmatched:
        print(f"매칭 안 됨 ({len(unmatched)}건):")
        for u in unmatched[:20]:
            print(f"  - {u}")
        if len(unmatched) > 20:
            print(f"  ... 외 {len(unmatched) - 20}건")

if __name__ == "__main__":
    main()
