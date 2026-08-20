import logging
import threading
import time

import jieba
from rank_bm25 import BM25Okapi

from .config import settings

logger = logging.getLogger(__name__)

# 减少 jieba 初始化日志噪音
jieba.setLogLevel(logging.WARNING)

_cache_lock = threading.Lock()
_corpus_cache = {}  # key -> {"expires_at": float, "chunks": list, "tokenized": list}


def tokenize(text):
    """中文分词，返回去除空白后的 token 列表"""
    return [t for t in jieba.cut(text) if t and t.strip()]


def bm25_rank(question, chunks, top_n):
    """对 chunks 做 BM25 关键词相关性排序，返回前 top_n（无缓存，供测试用）"""
    tokenized = [tokenize(c["chunk_text"]) for c in chunks]
    return _rank(question, chunks, tokenized, top_n)


def hybrid_rank(question, kb_ids, allowed_document_ids, fetch_chunks_fn, vector_rows, top_n):
    """BM25（带缓存）+ 向量 RRF 融合，返回融合后的 chunks 列表"""
    chunks, tokenized = _get_cached(kb_ids, allowed_document_ids, fetch_chunks_fn)
    bm25_rows = _rank(question, chunks, tokenized, top_n)
    fused_ids = rrf_fuse([vector_rows, bm25_rows])
    by_id = {c["id"]: c for c in chunks}
    return [by_id[i] for i in fused_ids if i in by_id][:top_n]


def invalidate_for_kb(kb_id):
    """文档索引/变更后失效该知识库的 BM25 缓存"""
    with _cache_lock:
        for key in list(_corpus_cache.keys()):
            if kb_id in key[0]:
                _corpus_cache.pop(key, None)


def rrf_fuse(ranked_lists, k=None):
    """多个已排序结果（每个元素含 id 字段）做 RRF 融合，返回按融合分降序的 id 列表"""
    k = k or settings.rrf_k
    scores = {}
    for ranked in ranked_lists:
        for rank, chunk in enumerate(ranked):
            scores[chunk["id"]] = scores.get(chunk["id"], 0.0) + 1.0 / (k + rank + 1)
    return sorted(scores, key=scores.get, reverse=True)


def _cache_key(kb_ids, allowed_document_ids=None):
    return (tuple(sorted(kb_ids or [])), tuple(sorted(allowed_document_ids or [])))


def _get_cached(kb_ids, allowed_document_ids, fetch_chunks_fn):
    key = _cache_key(kb_ids, allowed_document_ids)
    now = time.time()
    with _cache_lock:
        entry = _corpus_cache.get(key)
        if entry and entry["expires_at"] > now:
            return entry["chunks"], entry["tokenized"]

    chunks = fetch_chunks_fn()
    tokenized = [tokenize(c["chunk_text"]) for c in chunks]
    with _cache_lock:
        _corpus_cache[key] = {
            "expires_at": time.time() + settings.bm25_cache_ttl,
            "chunks": chunks,
            "tokenized": tokenized,
        }
    return chunks, tokenized


def _rank(question, chunks, tokenized, top_n):
    bm25 = BM25Okapi(tokenized)
    scores = bm25.get_scores(tokenize(question))
    ranked = sorted(zip(chunks, scores), key=lambda x: -x[1])
    return [c for c, _ in ranked[:top_n]]
