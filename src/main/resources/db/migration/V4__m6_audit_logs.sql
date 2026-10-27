-- M6: lịch sử thao tác nhạy cảm (ADR-023 ở infra/docs/adr): admin tạo ban tổ chức, tạo tài khoản; phát hiện
-- refresh token bị dùng lại. Ghi trong CÙNG transaction với thay đổi. Chỉ ghi thêm: trigger chặn UPDATE và DELETE.
-- Khác core: không chia partition, vì ở đây chỉ có thao tác của admin và sự kiện bảo mật, ít dòng.
CREATE TABLE audit_logs (
    id            uuid PRIMARY KEY,
    occurred_at   timestamptz NOT NULL,
    actor_type    varchar(10) NOT NULL,
    actor_id      uuid,
    actor_role    varchar(20),
    actor_name    varchar(60),
    action        varchar(50) NOT NULL,
    target_type   varchar(30) NOT NULL,
    target_id     uuid,
    details       jsonb NOT NULL DEFAULT '{}'::jsonb,
    CONSTRAINT audit_logs_actor_type_check CHECK (actor_type IN ('USER', 'SYSTEM')),
    CONSTRAINT audit_logs_user_has_id CHECK (actor_type <> 'USER' OR actor_id IS NOT NULL),
    CONSTRAINT audit_logs_system_has_name CHECK (actor_type <> 'SYSTEM' OR actor_name IS NOT NULL)
);

-- Trang quản trị: mới nhất trước, phân trang keyset (occurred_at, id)
CREATE INDEX idx_audit_logs_time ON audit_logs (occurred_at DESC, id DESC);

CREATE FUNCTION audit_logs_append_only() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION 'audit_logs chỉ được ghi thêm, không % được', TG_OP;
END
$$;

CREATE TRIGGER audit_logs_append_only BEFORE UPDATE OR DELETE ON audit_logs
    FOR EACH ROW EXECUTE FUNCTION audit_logs_append_only();
