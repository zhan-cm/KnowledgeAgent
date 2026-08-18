import json

import psycopg2
from psycopg2.extras import RealDictCursor

from .config import settings


def get_connection():
    return psycopg2.connect(settings.database_url)


def search(question_embedding, kb_ids, allowed_document_ids, top_k):
    vector_literal = json.dumps(question_embedding)
    sql = """
        SELECT dc.id, dc.document_id, d.title, dc.chunk_text, dc.page_number,
               1 - (dc.embedding <=> %s::vector) AS similarity
        FROM document_chunks dc
        JOIN documents d ON d.id = dc.document_id
        WHERE dc.kb_id = ANY(%s::bigint[])
    """
    params = [vector_literal, kb_ids]
    if allowed_document_ids:
        sql += " AND dc.document_id = ANY(%s::bigint[])"
        params.append(allowed_document_ids)
    sql += " ORDER BY dc.embedding <=> %s::vector LIMIT %s"
    params += [vector_literal, top_k]

    with get_connection() as conn, conn.cursor(cursor_factory=RealDictCursor) as cur:
        cur.execute(sql, params)
        return cur.fetchall()
