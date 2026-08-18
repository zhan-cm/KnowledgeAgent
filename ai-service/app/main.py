import logging

from fastapi import FastAPI

from .api import internal, query
from .core.config import settings
from .core.embedding import get_model
from .core.indexer import start_consumer

logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(name)s: %(message)s")
logger = logging.getLogger(__name__)

app = FastAPI(title="KnowledgeAgent AI Service", version="0.1.0")
app.include_router(query.router)
app.include_router(internal.router)


@app.on_event("startup")
def startup():
    logger.info("正在加载 Embedding 模型: %s", settings.embedding_model)
    get_model()
    start_consumer()


@app.on_event("shutdown")
def shutdown():
    logger.info("AI 服务已停止")
