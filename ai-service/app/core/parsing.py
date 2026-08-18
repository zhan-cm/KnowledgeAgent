from pathlib import Path


def parse_file(file_path):
    """解析文档，返回 [(页文本, 页码)]，页码从 1 开始"""
    ext = Path(file_path).suffix.lower()
    if ext == ".pdf":
        return _parse_pdf(file_path)
    if ext == ".docx":
        return _parse_docx(file_path)
    if ext == ".doc":
        raise ValueError("暂不支持旧版 .doc 格式，请转换为 .docx 后重新上传")
    if ext == ".txt":
        return _parse_txt(file_path)
    raise ValueError(f"不支持的文件类型: {ext}")


def _parse_pdf(path):
    import fitz

    pages = []
    with fitz.open(path) as doc:
        for i, page in enumerate(doc):
            text = page.get_text().strip()
            if text:
                pages.append((text, i + 1))
    return pages


def _parse_docx(path):
    import docx

    document = docx.Document(path)
    text = "\n".join(p.text for p in document.paragraphs if p.text.strip())
    return [(text, 1)] if text.strip() else []


def _parse_txt(path):
    for encoding in ("utf-8", "gbk", "utf-16"):
        try:
            text = Path(path).read_text(encoding=encoding)
            return [(text, 1)] if text.strip() else []
        except UnicodeDecodeError:
            continue
    raise ValueError("无法识别文本文件编码")
