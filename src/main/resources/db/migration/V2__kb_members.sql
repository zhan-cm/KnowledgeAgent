-- 知识库成员表：创建者默认为所有者（documents.created_by），成员分 VIEWER/EDITOR
CREATE TABLE kb_members (
    id BIGSERIAL PRIMARY KEY,
    kb_id BIGINT NOT NULL REFERENCES knowledge_bases(id) ON DELETE CASCADE,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    role VARCHAR(20) NOT NULL DEFAULT 'VIEWER',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_kb_member UNIQUE (kb_id, user_id)
);

CREATE INDEX idx_kb_members_user ON kb_members (user_id);
