import json
import logging

from fastapi import APIRouter, HTTPException
from fastapi.responses import StreamingResponse

from ..core.config import settings
from ..core.embedding import embed_texts
from ..core.llm import generate, stream_generate
from ..core.rerank import rerank
from ..core.retrieval import search
from ..models.schemas import Citation, QueryRequest, QueryResponse

logger = logging.getLogger(__name__)
router = APIRouter()


def _history_dicts(req):
    return [m.model_dump() for m in req.conversationHistory]


def _retrieve(req):
    question_embedding = embed_texts([req.question])[0]
    fetch_k = min(max(req.topK * 4, req.topK), settings.retrieval_k)
    rows = search(question_embedding, req.kbIds, req.allowedDocumentIds, fetch_k)
    if len(rows) > req.topK:
        rows = rerank(req.question, rows, req.topK)
    return rows


def _to_citations(rows):
    return [
        Citation(documentId=r["document_id"], title=r["title"],
                 chunkText=r["chunk_text"], page=r["page_number"])
        for r in rows
    ]


@router.post("/query", response_model=QueryResponse)
def query(req: QueryRequest) -> QueryResponse:
    if not req.kbIds:
        raise HTTPException(status_code=400, detail="kbIds 不能为空")
    try:
        rows = _retrieve(req)
        if not rows:
            return QueryResponse(answer="知识库中没有找到与问题相关的资料。", citations=[])
        answer = generate(req.question, rows, _history_dicts(req))
        return QueryResponse(answer=answer, citations=_to_citations(rows))
    except HTTPException:
        raise
    except Exception as e:
        logger.exception("问答处理失败")
        raise HTTPException(status_code=500, detail=f"问答处理失败: {e}")


def _sse(data: dict) -> str:
    return f"data: {json.dumps(data, ensure_ascii=False)}\n\n"


@router.post("/query/stream")
def query_stream(req: QueryRequest):
    if not req.kbIds:
        raise HTTPException(status_code=400, detail="kbIds 不能为空")

    def event_generator():
        try:
            rows = _retrieve(req)
            citations = [
                {"documentId": r["document_id"], "title": r["title"],
                 "chunkText": r["chunk_text"], "page": r["page_number"]}
                for r in rows
            ]
            yield _sse({"type": "citations", "citations": citations})
            if not rows:
                yield _sse({"type": "delta", "content": "知识库中没有找到与问题相关的资料。"})
                yield _sse({"type": "done"})
                return
            history = _history_dicts(req)
            for delta in stream_generate(req.question, rows, history):
                yield _sse({"type": "delta", "content": delta})
            yield _sse({"type": "done"})
        except Exception as e:
            logger.exception("流式问答失败")
            yield _sse({"type": "error", "message": f"问答处理失败: {e}"})

    return StreamingResponse(
        event_generator(),
        media_type="text/event-stream",
        headers={"Cache-Control": "no-cache", "X-Accel-Buffering": "no"},
    )
