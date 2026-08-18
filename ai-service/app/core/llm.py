import json
import time

import requests

from .config import settings

SYSTEM_PROMPT = (
    "你是一个企业知识库助手。请仅根据提供的参考资料回答用户问题，"
    "并在回答中用 [1]、[2] 等编号标注引用。如果资料中没有答案，请如实说明。"
)


def build_prompt(question, chunks):
    references = "\n\n".join(f"[{i}] {c['chunk_text']}" for i, c in enumerate(chunks, 1))
    return f"参考资料：\n{references}\n\n用户问题：{question}\n请基于上述资料回答（引用资料编号）："


def build_messages(question, chunks, history=None):
    messages = [{"role": "system", "content": SYSTEM_PROMPT}]
    for entry in history or []:
        role = entry.get("role")
        content = (entry.get("content") or "").strip()
        if role in ("user", "assistant") and content:
            messages.append({"role": role, "content": content})
    messages.append({"role": "user", "content": build_prompt(question, chunks)})
    return messages


def _headers():
    return {"Authorization": f"Bearer {settings.deepseek_api_key}"}


def _base_payload(question, chunks, history, stream):
    return {
        "model": settings.deepseek_model,
        "messages": build_messages(question, chunks, history),
        "temperature": 0.3,
        "max_tokens": 1024,
        "stream": stream,
    }


def _api_url():
    return f"{settings.deepseek_base_url.rstrip('/')}/chat/completions"


def generate(question, chunks, history=None):
    if not settings.deepseek_api_key:
        raise RuntimeError("未配置 DEEPSEEK_API_KEY，请在 ai-service/.env 中填写")
    payload = _base_payload(question, chunks, history, stream=False)
    last_error = None
    for attempt in range(3):
        try:
            response = requests.post(_api_url(), headers=_headers(), json=payload, timeout=120)
            response.raise_for_status()
            return response.json()["choices"][0]["message"]["content"]
        except Exception as e:
            last_error = e
            time.sleep(2 * (attempt + 1))
    raise RuntimeError(f"DeepSeek API 调用失败: {last_error}")


def stream_generate(question, chunks, history=None):
    """流式生成，逐段产出文本增量"""
    if not settings.deepseek_api_key:
        raise RuntimeError("未配置 DEEPSEEK_API_KEY，请在 ai-service/.env 中填写")
    payload = _base_payload(question, chunks, history, stream=True)
    with requests.post(_api_url(), headers=_headers(), json=payload,
                       stream=True, timeout=(10, 120)) as response:
        response.raise_for_status()
        for line in response.iter_lines(decode_unicode=True):
            if not line or not line.startswith("data:"):
                continue
            data = line[5:].strip()
            if data == "[DONE]":
                break
            try:
                content = json.loads(data)["choices"][0]["delta"].get("content", "")
            except (json.JSONDecodeError, KeyError, IndexError):
                continue
            if content:
                yield content
