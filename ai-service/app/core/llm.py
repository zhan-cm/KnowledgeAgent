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


def generate(question, chunks):
    if not settings.deepseek_api_key:
        raise RuntimeError("未配置 DEEPSEEK_API_KEY，请在 ai-service/.env 中填写")
    url = f"{settings.deepseek_base_url.rstrip('/')}/chat/completions"
    payload = {
        "model": settings.deepseek_model,
        "messages": [
            {"role": "system", "content": SYSTEM_PROMPT},
            {"role": "user", "content": build_prompt(question, chunks)},
        ],
        "temperature": 0.3,
        "max_tokens": 1024,
        "stream": False,
    }
    headers = {"Authorization": f"Bearer {settings.deepseek_api_key}"}

    last_error = None
    for attempt in range(3):
        try:
            response = requests.post(url, headers=headers, json=payload, timeout=120)
            response.raise_for_status()
            return response.json()["choices"][0]["message"]["content"]
        except Exception as e:
            last_error = e
            time.sleep(2 * (attempt + 1))
    raise RuntimeError(f"DeepSeek API 调用失败: {last_error}")
