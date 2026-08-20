import logging

from .config import settings

logger = logging.getLogger(__name__)

# 默认兜底词表（占位，生产请在 .env 的 SENSITIVE_WORDS 中配置完整词表）
_DEFAULT_WORDS = ["赌博", "色情", "暴力", "毒品", "枪支弹药"]


def _words():
    words = set(_DEFAULT_WORDS)
    for w in (settings.sensitive_words or "").split(","):
        w = w.strip()
        if w:
            words.add(w)
    return words


def contains(text):
    text = text or ""
    return any(w in text for w in _words())


def mask(text):
    text = text or ""
    for w in sorted(_words(), key=len, reverse=True):
        if w:
            text = text.replace(w, "*" * len(w))
    return text
