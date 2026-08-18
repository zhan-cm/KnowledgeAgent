import logging
import threading

from .config import resolve_local_path, settings

logger = logging.getLogger(__name__)

_lock = threading.Lock()
_model = None
_load_failed = False


def get_model():
    global _model, _load_failed
    with _lock:
        if _model is None and not _load_failed:
            try:
                from sentence_transformers import CrossEncoder

                model_path = resolve_local_path(settings.rerank_model)
                logger.info("正在加载重排序模型: %s", model_path)
                _model = CrossEncoder(model_path)
            except Exception as e:
                _load_failed = True
                logger.warning("重排序模型加载失败，将跳过重排序（可设 RERANK_ENABLED=false 关闭）: %s", e)
        return _model


def rerank(question, rows, top_n):
    """按 (问题, 片段) 相关度重排 rows，返回前 top_n 条。模型不可用时原样截断返回。"""
    if not settings.rerank_enabled or len(rows) <= top_n:
        return rows[:top_n]
    model = get_model()
    if model is None:
        return rows[:top_n]
    try:
        pairs = [(question, r["chunk_text"]) for r in rows]
        scores = model.predict(pairs, batch_size=8)
        ranked = sorted(zip(rows, scores), key=lambda x: -x[1])
        return [r for r, _ in ranked[:top_n]]
    except Exception as e:
        logger.warning("重排序执行失败，退回原始检索顺序: %s", e)
        return rows[:top_n]
