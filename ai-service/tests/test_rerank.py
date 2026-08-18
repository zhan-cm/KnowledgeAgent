from app.core import rerank as rerank_module
from app.core.config import settings


def test_rerank_sorts_by_score(monkeypatch):
    rows = [{"chunk_text": "a"}, {"chunk_text": "b"}, {"chunk_text": "c"}]

    class FakeModel:
        def predict(self, pairs, batch_size=None):
            assert pairs == [("问题", "a"), ("问题", "b"), ("问题", "c")]
            return [0.1, 0.9, 0.5]

    monkeypatch.setattr(rerank_module, "_model", FakeModel())
    monkeypatch.setattr(rerank_module, "_load_failed", False)
    monkeypatch.setattr(settings, "rerank_enabled", True)
    result = rerank_module.rerank("问题", rows, 2)
    assert [r["chunk_text"] for r in result] == ["b", "c"]


def test_rerank_returns_original_order_when_disabled(monkeypatch):
    rows = [{"chunk_text": "a"}, {"chunk_text": "b"}]
    monkeypatch.setattr(settings, "rerank_enabled", False)
    result = rerank_module.rerank("q", rows, 1)
    assert result[0]["chunk_text"] == "a"


def test_rerank_falls_back_when_model_load_failed(monkeypatch):
    rows = [{"chunk_text": "a"}, {"chunk_text": "b"}]
    monkeypatch.setattr(rerank_module, "_model", None)
    monkeypatch.setattr(rerank_module, "_load_failed", True)
    monkeypatch.setattr(settings, "rerank_enabled", True)
    result = rerank_module.rerank("q", rows, 1)
    assert result[0]["chunk_text"] == "a"


def test_rerank_returns_all_when_fewer_than_top_n(monkeypatch):
    rows = [{"chunk_text": "a"}]
    monkeypatch.setattr(settings, "rerank_enabled", True)
    result = rerank_module.rerank("q", rows, 5)
    assert result == rows


def test_rerank_recovers_on_predict_error(monkeypatch):
    rows = [{"chunk_text": "a"}, {"chunk_text": "b"}]

    class BrokenModel:
        def predict(self, pairs, batch_size=None):
            raise RuntimeError("boom")

    monkeypatch.setattr(rerank_module, "_model", BrokenModel())
    monkeypatch.setattr(settings, "rerank_enabled", True)
    result = rerank_module.rerank("q", rows, 1)
    assert result[0]["chunk_text"] == "a"
