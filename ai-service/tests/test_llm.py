from app.core.llm import build_messages, build_prompt


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
