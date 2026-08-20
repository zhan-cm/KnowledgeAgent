import json
import logging
import threading
import time

import redis
import requests

from .config import settings
from .chunking import chunk_text
from .embedding import embed_texts
from .hybrid import invalidate_for_kb
from .parsing import parse_file
from .retrieval import get_connection

logger = logging.getLogger(__name__)


def start_consumer():
    thread = threading.Thread(target=_consume_loop, name="index-consumer", daemon=True)
    thread.start()
    logger.info("索引消费者线程已启动（stream=%s）", settings.index_stream)


def _consume_loop():
    client = redis.Redis.from_url(
        settings.redis_url, decode_responses=True,
        socket_timeout=15, socket_connect_timeout=5)
    try:
        client.xgroup_create(settings.index_stream, settings.consumer_group, id="0", mkstream=True)
    except redis.exceptions.ResponseError:
        pass

    while True:
        try:
            messages = client.xreadgroup(
                settings.consumer_group, settings.consumer_name,
                {settings.index_stream: ">"}, block=5000, count=1)
            for _stream, entries in messages:
                for message_id, fields in entries:
                    document_id = fields.get("documentId")
                    try:
                        _process(fields)
                        _callback(document_id, "INDEXED")
                    except Exception as e:
                        logger.exception("文档索引失败: %s", fields)
                        _callback(document_id, "FAILED", str(e)[:500])
                    finally:
                        client.xack(settings.index_stream, settings.consumer_group, message_id)
        except redis.exceptions.TimeoutError:
            continue
        except Exception:
            logger.exception("消费循环异常，5 秒后重试")
            time.sleep(5)


def _process(fields):
    document_id = int(fields["documentId"])
    kb_id = int(fields["kbId"])
    file_path = fields["filePath"]

    pages = parse_file(file_path)
    if not pages:
        raise ValueError("文档解析后无文本内容（可能是扫描版 PDF）")
    chunks = chunk_text(pages, settings.chunk_size, settings.chunk_overlap)
    if not chunks:
        raise ValueError("文档分块后无内容")
    embeddings = embed_texts([c["text"] for c in chunks])
    _insert_chunks(document_id, kb_id, chunks, embeddings)
    invalidate_for_kb(kb_id)


def _insert_chunks(document_id, kb_id, chunks, embeddings):
    with get_connection() as conn, conn.cursor() as cur:
        cur.execute("DELETE FROM document_chunks WHERE document_id = %s", (document_id,))
        rows = [
            (document_id, kb_id, chunk["text"], i, chunk["page"], "{}", json.dumps(embedding))
            for i, (chunk, embedding) in enumerate(zip(chunks, embeddings))
        ]
        cur.executemany(
            "INSERT INTO document_chunks (document_id, kb_id, chunk_text, chunk_index, page_number, metadata, embedding) "
            "VALUES (%s, %s, %s, %s, %s, %s::jsonb, %s::vector)",
            rows)


def _callback(document_id, status, error=None):
    if document_id is None:
        return
    payload = {"status": status}
    if error:
        payload["error"] = error
    try:
        response = requests.put(
            f"{settings.java_base_url.rstrip('/')}/internal/documents/{document_id}/status",
            json=payload,
            headers={"X-Internal-Token": settings.internal_token},
            timeout=10)
        response.raise_for_status()
    except Exception:
        logger.exception("回调 Java 更新状态失败 document_id=%s", document_id)
