import re


def chunk_text(pages, chunk_size=500, overlap=50):
    """按页分块。pages: [(text, page_number)]，返回 [{"text": ..., "page": ...}]"""
    chunks = []
    for text, page in pages:
        text = _clean(text)
        if not text:
            continue
        start = 0
        length = len(text)
        while start < length:
            end = min(start + chunk_size, length)
            piece = text[start:end].strip()
            if piece:
                chunks.append({"text": piece, "page": page})
            if end >= length:
                break
            start = end - overlap
    return chunks


def _clean(text):
    text = re.sub(r"[\x00-\x08\x0b\x0c\x0e-\x1f]", "", text)
    text = re.sub(r"[ \t]+", " ", text)
    text = re.sub(r"\n{3,}", "\n\n", text)
    return text.strip()
