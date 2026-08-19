import json
import logging
import time

import requests

from .config import settings

logger = logging.getLogger(__name__)

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


def _chat(messages, temperature, max_tokens):
    """非流式对话，带 3 次重试"""
    if not settings.deepseek_api_key:
        raise RuntimeError("未配置 DEEPSEEK_API_KEY，请在 ai-service/.env 中填写")
    payload = {
        "model": settings.deepseek_model,
        "messages": messages,
        "temperature": temperature,
        "max_tokens": max_tokens,
        "stream": False,
    }
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


def generate(question, chunks, history=None):
    return _chat(build_messages(question, chunks, history), temperature=0.3, max_tokens=1024)


def rewrite_question(question, history):
    """结合对话历史把追问改写为独立完整的问题；失败时返回原问题"""
    try:
        history_text = "\n".join(f"{h['role']}: {h['content']}" for h in history or [])
        messages = [
            {"role": "system",
             "content": "你是查询改写助手。结合对话历史，把用户当前问题改写为独立、完整、明确的问题。"
                        "直接输出改写后的问题，不要任何解释。"},
            {"role": "user",
             "content": f"对话历史：\n{history_text}\n\n当前问题：{question}\n改写后的问题："},
        ]
        rewritten = _chat(messages, temperature=0, max_tokens=100).strip()
        if not rewritten:
            return question
        logger.info("查询改写: %s -> %s", question, rewritten)
        return rewritten
    except Exception as e:
        logger.warning("查询改写失败，使用原问题: %s", e)
        return question


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
