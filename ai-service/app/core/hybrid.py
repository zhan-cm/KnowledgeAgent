import logging

import jieba
from rank_bm25 import BM25Okapi

from .config import settings

logger = logging.getLogger(__name__)

# 减少 jieba 初始化日志噪音
jieba.setLogLevel(logging.WARNING)


def tokenize(text):
    """中文分词，返回去除空白后的 token 列表"""
    return [t for t in jieba.cut(text) if t and t.strip()]


def bm25_rank(question, chunks, top_n):
    """对 chunks 做 BM25 关键词相关性排序，返回前 top_n（含原始字段，含 id）"""
    corpus = [tokenize(c["chunk_text"]) for c in chunks]
    bm25 = BM25Okapi(corpus)
    scores = bm25.get_scores(tokenize(question))
    ranked = sorted(zip(chunks, scores), key=lambda x: -x[1])
    return [c for c, _ in ranked[:top_n]]


def rrf_fuse(ranked_lists, k=None):
    """多个已排序结果（每个元素含 id 字段）做 RRF 融合，返回按融合分降序的 id 列表"""
    k = k or settings.rrf_k
    scores = {}
    for ranked in ranked_lists:
        for rank, chunk in enumerate(ranked):
            scores[chunk["id"]] = scores.get(chunk["id"], 0.0) + 1.0 / (k + rank + 1)
    return sorted(scores, key=scores.get, reverse=True)
