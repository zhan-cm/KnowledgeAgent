import pytest
from pydantic import ValidationError

from app.models.schemas import QueryRequest


def test_valid_request_with_defaults():
    req = QueryRequest(question="问题", kbIds=[1])
    assert req.topK == 5
    assert req.allowedDocumentIds == []
    assert req.conversationHistory == []


def test_topk_out_of_bounds_rejected():
    with pytest.raises(ValidationError):
        QueryRequest(question="问题", topK=100)


def test_empty_question_rejected():
    with pytest.raises(ValidationError):
        QueryRequest(question="", kbIds=[1])


def test_history_messages_parsed():
    req = QueryRequest(question="问题", conversationHistory=[{"role": "user", "content": "之前"}])
    assert req.conversationHistory[0].role == "user"
    assert req.conversationHistory[0].content == "之前"
