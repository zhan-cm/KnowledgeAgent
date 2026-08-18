import pytest

from app.core.parsing import parse_file


def test_parse_txt_utf8(tmp_path):
    f = tmp_path / "a.txt"
    f.write_text("你好世界", encoding="utf-8")
    pages = parse_file(str(f))
    assert pages[0][0] == "你好世界"
    assert pages[0][1] == 1


def test_parse_txt_gbk_fallback(tmp_path):
    f = tmp_path / "b.txt"
    f.write_text("中文内容", encoding="gbk")
    pages = parse_file(str(f))
    assert pages[0][0] == "中文内容"


def test_parse_legacy_doc_rejected(tmp_path):
    f = tmp_path / "c.doc"
    f.write_text("x")
    with pytest.raises(ValueError, match="旧版"):
        parse_file(str(f))


def test_parse_unsupported_extension_rejected(tmp_path):
    f = tmp_path / "d.exe"
    f.write_text("x")
    with pytest.raises(ValueError, match="不支持"):
        parse_file(str(f))
