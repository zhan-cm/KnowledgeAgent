import logging
import threading

from .config import resolve_local_path, settings

logger = logging.getLogger(__name__)

_lock = threading.Lock()
_model = None


def get_model():
    global _model
    with _lock:
        if _model is None:
            from sentence_transformers import SentenceTransformer

            model_path = resolve_local_path(settings.embedding_model)
            if model_path == settings.embedding_model:
                logger.warning("模型 %s 不是本地目录，将尝试从 HuggingFace 下载（可能很慢或失败）",
                               settings.embedding_model)
            logger.info("正在加载 Embedding 模型: %s", model_path)
            _model = SentenceTransformer(model_path)
        return _model


def embed_texts(texts):
    model = get_model()
    vectors = model.encode(texts, normalize_embeddings=True)
    return [v.tolist() for v in vectors]
