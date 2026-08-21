#!/usr/bin/env python
"""检索质量评测：对 dataset.json 里的每个问题跑一次 /query，检查引用是否命中预期文档。

用法（需 AI 服务已在 8000 端口运行）：
    python eval/run_eval.py [top_k]
"""
import json
import sys
from pathlib import Path

import requests

BASE_URL = "http://localhost:8000"
DATASET = Path(__file__).parent / "dataset.json"


def main(top_k=5):
    cases = json.loads(DATASET.read_text(encoding="utf-8"))
    hits = 0
    for case in cases:
        resp = requests.post(f"{BASE_URL}/query", json={
            "question": case["question"],
            "kbIds": [1],
            "allowedDocumentIds": [],
            "topK": top_k,
        }, timeout=120)
        data = resp.json()
        citations = data.get("citations", [])
        hit = any(case["expected"] in c.get("title", "") for c in citations)
        titles = [c.get("title", "") for c in citations]
        if hit:
            hits += 1
        print(f"[{'✓' if hit else '✗'}] {case['question']} -> {titles}")
    rate = hits / len(cases) if cases else 0
    print(f"\nRecall@{top_k}: {hits}/{len(cases)} = {rate:.2%}")


if __name__ == "__main__":
    k = int(sys.argv[1]) if len(sys.argv) > 1 else 5
    main(k)
