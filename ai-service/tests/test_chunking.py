from app.core.chunking import chunk_text


def test_basic_chunking_with_overlap():
    pages = [("a" * 500 + "b" * 500, 1)]
    chunks = chunk_text(pages, chunk_size=500, overlap=50)
    assert len(chunks) == 3
    assert all(c["page"] == 1 for c in chunks)
    assert chunks[0]["text"] == "a" * 500
    assert chunks[1]["text"] == "a" * 50 + "b" * 450
    assert chunks[2]["text"] == "b" * 100


def test_empty_pages():
    assert chunk_text([]) == []


def test_blank_page_skipped():
    pages = [("   \n\n  ", 1), ("有效内容", 2)]
    chunks = chunk_text(pages, chunk_size=100, overlap=0)
    assert len(chunks) == 1
    assert chunks[0]["page"] == 2


def test_page_number_tracked_per_page():
    pages = [("第一页内容", 1), ("第二页内容", 3)]
    chunks = chunk_text(pages, chunk_size=100, overlap=0)
    assert [c["page"] for c in chunks] == [1, 3]


def test_cleaning_removes_control_chars_and_squashes_whitespace():
    pages = [("hello\x00world\n\n\n\nfoo  bar", 1)]
    chunks = chunk_text(pages, chunk_size=100, overlap=0)
    text = chunks[0]["text"]
    assert "\x00" not in text
    assert "foo bar" in text
    assert "\n\n\n\n" not in text
