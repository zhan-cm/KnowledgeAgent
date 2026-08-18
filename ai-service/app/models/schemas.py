from pydantic import BaseModel, Field


class QueryRequest(BaseModel):
    question: str = Field(..., min_length=1, max_length=2000)
    kbIds: list[int] = Field(default_factory=list)
    allowedDocumentIds: list[int] = Field(default_factory=list)
    topK: int = Field(default=5, ge=1, le=20)


class Citation(BaseModel):
    documentId: int
    title: str
    chunkText: str
    page: int | None = None


class QueryResponse(BaseModel):
    answer: str
    citations: list[Citation] = Field(default_factory=list)
