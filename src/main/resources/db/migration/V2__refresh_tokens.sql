-- Refresh token (cookie httpOnly). Chỉ lưu SHA-256 của token, không lưu token gốc.
-- Mỗi lần dùng, token cũ bị thu hồi và thay bằng token mới cùng family. Token đã thu hồi mà còn được gửi lại nghĩa là
-- có người giữ bản sao: thu hồi cả family (README, mục "Refresh token").

CREATE TABLE refresh_tokens (
    id          uuid PRIMARY KEY,
    user_id     uuid NOT NULL,
    family_id   uuid NOT NULL,
    token_hash  varchar(64) NOT NULL,
    created_at  timestamptz NOT NULL,
    expires_at  timestamptz NOT NULL,
    revoked_at  timestamptz,
    CONSTRAINT fk_refresh_tokens_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT uq_refresh_tokens_hash UNIQUE (token_hash)
);
CREATE INDEX idx_refresh_tokens_family ON refresh_tokens (family_id) WHERE revoked_at IS NULL;
CREATE INDEX idx_refresh_tokens_user ON refresh_tokens (user_id);
