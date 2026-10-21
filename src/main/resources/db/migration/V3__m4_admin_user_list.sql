-- M4: trang quản trị với 2 triệu tài khoản. Danh sách đi bằng keyset (created_at, id) thay vì OFFSET, và tìm chuỗi con
-- trong email/họ tên bằng index trigram (ADR-018 ở core-service/docs/adr).

-- Danh sách mới tạo trước; id phá hoà khi nhiều tài khoản tạo cùng một micro giây
CREATE INDEX idx_users_created ON users (created_at DESC, id DESC);
CREATE INDEX idx_users_role_created ON users (role, created_at DESC, id DESC);

-- LIKE '%chuỗi%' không dùng được index B-tree; index trigram (pg_trgm) thì dùng được khi chuỗi tìm từ 3 ký tự.
-- pg_trgm là extension "trusted": chủ database (user identity) tạo được, không cần superuser.
CREATE EXTENSION IF NOT EXISTS pg_trgm;
CREATE INDEX idx_users_email_trgm ON users USING gin (email gin_trgm_ops);
CREATE INDEX idx_users_full_name_trgm ON users USING gin (lower(full_name) gin_trgm_ops);
