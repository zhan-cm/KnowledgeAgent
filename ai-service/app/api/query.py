import logging

from fastapi import APIRouter, HTTPException

from ..core.embedding import embed_texts
from ..core.llm import generate
from ..core.retrieval import search
from ..models.schemas import Citation, QueryRequest, QueryResponse

logger = logging.getLogger(__name__)
router = APIRouter()


@router.post("/query", response_model=QueryResponse)
def query(req: QueryRequest) -> QueryResponse:
    if not req.kbIds:
        raise HTTPException(status_code=400, detail="kbIds 不能为空")
    try:
        question_embedding = embed_texts([req.question])[0]
        rows = search(question_embedding, req.kbIds, req.allowedDocumentIds, req.topK)
        if not rows:
            return QueryResponse(answer="知识库中没有找到与问题相关的资料。", citations=[])
        answer = generate(req.question, rows)
        citations = [
            Citation(documentId=r["document_id"], title=r["title"],
                     chunkText=r["chunk_text"], page=r["page_number"])
            for r in rows
        ]
        return QueryResponse(answer=answer, citations=citations)
    except HTTPException:
        raise
    except Exception as e:
        logger.exception("问答处理失败")
        raise HTTPException(status_code=500, detail=f"问答处理失败: {e}")
