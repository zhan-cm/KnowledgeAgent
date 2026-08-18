import logging
import threading
from pathlib import Path

from .config import BASE_DIR, settings

logger = logging.getLogger(__name__)

_lock = threading.Lock()
_model = None


def _resolve_model_path(name):
    """本地路径优先解析为绝对路径；找不到时才回落到 HuggingFace 仓库名"""
    candidate = Path(name)
    if candidate.is_dir():
        return str(candidate.resolve())
    absolute = BASE_DIR / name
    if absolute.is_dir():
        return str(absolute.resolve())
    logger.warning("模型 %s 不是本地目录，将尝试从 HuggingFace 下载（可能很慢或失败）", name)
    return name


def get_model():
    global _model
    with _lock:
        if _model is None:
            from sentence_transformers import SentenceTransformer

            model_path = _resolve_model_path(settings.embedding_model)
            logger.info("正在加载 Embedding 模型: %s", model_path)
            _model = SentenceTransformer(model_path)
        return _model


def embed_texts(texts):
    model = get_model()
    vectors = model.encode(texts, normalize_embeddings=True)
    return [v.tolist() for v in vectors]
