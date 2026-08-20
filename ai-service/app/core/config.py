import os
from dataclasses import dataclass
from pathlib import Path

from dotenv import load_dotenv

BASE_DIR = Path(__file__).resolve().parent.parent.parent  # ai-service/
load_dotenv(BASE_DIR / ".env")


def resolve_local_path(name: str) -> str:
    """本地路径优先解析为绝对路径（相对 ai-service/），否则原样返回（视为 HuggingFace 仓库名）"""
    candidate = Path(name)
    if candidate.is_dir():
        return str(candidate.resolve())
    absolute = BASE_DIR / name
    if absolute.is_dir():
        return str(absolute.resolve())
    return name


@dataclass
class Settings:
    database_url: str = os.getenv("DATABASE_URL", "postgresql://admin:password@localhost:5432/knowledge_agent")
    redis_url: str = os.getenv("REDIS_URL", "redis://localhost:6379/0")
    java_base_url: str = os.getenv("JAVA_BASE_URL", "http://localhost:8080")
    internal_token: str = os.getenv("INTERNAL_TOKEN", "knowledge-agent-internal-token-2026-change-me")
    deepseek_api_key: str = os.getenv("DEEPSEEK_API_KEY", "")
    deepseek_base_url: str = os.getenv("DEEPSEEK_BASE_URL", "https://api.deepseek.com")
    deepseek_model: str = os.getenv("DEEPSEEK_MODEL", "deepseek-chat")
    embedding_model: str = os.getenv("EMBEDDING_MODEL", "BAAI/bge-small-zh")
    index_stream: str = os.getenv("INDEX_STREAM", "index-requests")
    consumer_group: str = os.getenv("CONSUMER_GROUP", "index-group")
    consumer_name: str = os.getenv("CONSUMER_NAME", "index-worker-1")
    chunk_size: int = int(os.getenv("CHUNK_SIZE", "500"))
    chunk_overlap: int = int(os.getenv("CHUNK_OVERLAP", "50"))
    rerank_enabled: bool = os.getenv("RERANK_ENABLED", "true").lower() == "true"
    rerank_model: str = os.getenv("RERANK_MODEL", "BAAI/bge-reranker-base")
    retrieval_k: int = int(os.getenv("RETRIEVAL_K", "20"))
    rewrite_enabled: bool = os.getenv("REWRITE_ENABLED", "true").lower() == "true"
    hybrid_enabled: bool = os.getenv("HYBRID_ENABLED", "true").lower() == "true"
    rrf_k: int = int(os.getenv("RRF_K", "60"))


settings = Settings()
