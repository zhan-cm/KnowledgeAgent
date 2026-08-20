from app.core import sensitive


def test_contains_detects_default_word():
    assert sensitive.contains("这个赌博平台怎么样") is True
    assert sensitive.contains("正常的报销流程") is False


def test_mask_replaces_sensitive_words():
    text = "内容包括赌博和色情内容"
    masked = sensitive.mask(text)
    assert "赌博" not in masked
    assert "色情" not in masked
    assert "*" in masked


def test_custom_words_from_settings(monkeypatch):
    from app.core.config import settings
    monkeypatch.setattr(settings, "sensitive_words", "内测,机密")
    assert sensitive.contains("这个内测功能") is True
    assert sensitive.contains("机密文档") is True
