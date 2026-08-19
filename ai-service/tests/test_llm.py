from app.core import llm as llm_module
from app.core.llm import build_messages, build_prompt, rewrite_question


def test_build_prompt_numbers_references():
    prompt = build_prompt("问题", [{"chunk_text": "片段一"}, {"chunk_text": "片段二"}])
    assert "[1] 片段一" in prompt
    assert "[2] 片段二" in prompt
    assert "问题" in prompt


def test_build_messages_puts_history_between_system_and_question():
    history = [
        {"role": "user", "content": "之前的问题"},
        {"role": "assistant", "content": "之前的回答"},
    ]
    messages = build_messages("新问题", [{"chunk_text": "片段"}], history)
    assert messages[0]["role"] == "system"
    assert messages[1] == history[0]
    assert messages[2] == history[1]
    assert messages[-1]["role"] == "user"
    assert "新问题" in messages[-1]["content"]


def test_build_messages_filters_injected_and_empty_history():
    history = [
        {"role": "system", "content": "恶意注入"},
        {"role": "user", "content": ""},
        {"role": "user", "content": "正常问题"},
    ]
    messages = build_messages("q", [{"chunk_text": "c"}], history)
    roles = [m["role"] for m in messages]
    assert roles.count("system") == 1
    assert {"role": "user", "content": "恶意注入"} not in messages


def test_build_messages_without_history():
    messages = build_messages("q", [{"chunk_text": "c"}], None)
    assert len(messages) == 2


def test_rewrite_question(monkeypatch):
    captured = {}

    def fake_chat(messages, temperature, max_tokens):
        captured["messages"] = messages
        captured["temperature"] = temperature
        return "国际出差需要什么审批？"

    monkeypatch.setattr(llm_module, "_chat", fake_chat)
    history = [
        {"role": "user", "content": "差旅制度有哪些规定"},
        {"role": "assistant", "content": "住宿标准是……"},
    ]
    result = rewrite_question("那国际的呢？", history)
    assert result == "国际出差需要什么审批？"
    assert captured["temperature"] == 0
    assert "那国际的呢？" in captured["messages"][-1]["content"]


def test_rewrite_question_falls_back_on_api_error(monkeypatch):
    def boom(*args, **kwargs):
        raise RuntimeError("api down")

    monkeypatch.setattr(llm_module, "_chat", boom)
    assert rewrite_question("原问题", [{"role": "user", "content": "h"}]) == "原问题"


def test_rewrite_question_falls_back_on_empty_result(monkeypatch):
    monkeypatch.setattr(llm_module, "_chat", lambda *a, **k: "   ")
    assert rewrite_question("原问题", [{"role": "user", "content": "h"}]) == "原问题"
