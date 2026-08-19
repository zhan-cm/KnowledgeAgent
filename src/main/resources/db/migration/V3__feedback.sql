-- 问答反馈表：每个用户对每条助手消息一条反馈（UP/DOWN）
CREATE TABLE feedback (
    id BIGSERIAL PRIMARY KEY,
    message_id BIGINT NOT NULL REFERENCES messages(id) ON DELETE CASCADE,
    user_id BIGINT NOT NULL REFERENCES users(id),
    rating VARCHAR(10) NOT NULL,
    comment TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_feedback UNIQUE (message_id, user_id)
);
