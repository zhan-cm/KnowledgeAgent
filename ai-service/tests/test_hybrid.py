from app.core import hybrid
from app.core.hybrid import bm25_rank, hybrid_rank, invalidate_for_kb, rrf_fuse, tokenize


def test_tokenize_chinese():
    tokens = tokenize("出差住宿费标准是多少")
    assert len(tokens) >= 2
    assert all(t.strip() for t in tokens)


def test_bm25_rank_prefers_keyword_match():
    chunks = [
        {"chunk_text": "差旅住宿标准：一线城市每晚不超过500元", "id": 1},
        {"chunk_text": "办公用品采购需提交需求清单并审批", "id": 2},
        {"chunk_text": "员工考勤打卡与补卡流程说明", "id": 3},
    ]
    ranked = bm25_rank("住宿标准是多少", chunks, top_n=2)
    assert ranked[0]["id"] == 1


def test_rrf_fuse_combines_rankings():
    vector_ranked = [{"id": 1}, {"id": 2}, {"id": 3}]
    bm25_ranked = [{"id": 2}, {"id": 4}, {"id": 1}]
    ids = rrf_fuse([vector_ranked, bm25_ranked], k=60)
    assert ids[0] == 2  # 两侧都靠前
    assert ids[1] == 1
    assert set(ids) == {1, 2, 3, 4}


def test_hybrid_rank_caches_fetch(monkeypatch):
    monkeypatch.setattr(hybrid.settings, "bm25_cache_ttl", 300)
    invalidate_for_kb(1)

    calls = {"n": 0}
    chunks = [
        {"id": 1, "chunk_text": "差旅住宿标准一线城市不超过500元"},
        {"id": 2, "chunk_text": "办公用品采购需提交审批清单"},
    ]
    vector_rows = [{"id": 1}, {"id": 2}]

    def fake_fetch():
        calls["n"] += 1
        return chunks

    r1 = hybrid_rank("住宿标准", [1], [], fake_fetch, vector_rows, 2)
    r2 = hybrid_rank("住宿标准", [1], [], fake_fetch, vector_rows, 2)

    assert calls["n"] == 1  # 第二次命中缓存
    assert [c["id"] for c in r1] == [c["id"] for c in r2]


def test_invalidate_forces_refetch(monkeypatch):
    monkeypatch.setattr(hybrid.settings, "bm25_cache_ttl", 300)
    invalidate_for_kb(1)

    calls = {"n": 0}
    chunks = [{"id": 1, "chunk_text": "内容一"}]
    vector_rows = [{"id": 1}]

    def fake_fetch():
        calls["n"] += 1
        return chunks

    hybrid_rank("q", [1], [], fake_fetch, vector_rows, 1)
    invalidate_for_kb(1)
    hybrid_rank("q", [1], [], fake_fetch, vector_rows, 1)

    assert calls["n"] == 2  # invalidate 后重新拉取
