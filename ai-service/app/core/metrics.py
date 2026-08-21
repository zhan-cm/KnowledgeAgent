from prometheus_client import CONTENT_TYPE_LATEST, Counter, Histogram, generate_latest

QUERY_TOTAL = Counter("ai_query_total", "问答请求总数")
QUERY_DURATION = Histogram("ai_query_duration_seconds", "问答各阶段耗时", labelnames=["phase"])
INDEX_TOTAL = Counter("ai_index_total", "文档索引请求总数")
INDEX_DURATION = Histogram("ai_index_duration_seconds", "文档索引耗时")


def render_metrics():
    return generate_latest(), CONTENT_TYPE_LATEST
