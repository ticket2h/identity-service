package com.ticket2h.identity.service;

/**
 * Thao tác được ghi vào lịch sử (ADR-023 ở infra/docs/adr). Thêm thao tác mới: thêm hằng ở đây (kèm loại đối
 * tượng) và nhãn tiếng Việt ở trang quản trị của web-app.
 */
public enum AuditAction {
    ORGANIZER_CREATED(Target.ORGANIZER),
    /** Admin tạo tài khoản (khách tự đăng ký thì không ghi). */
    USER_CREATED(Target.USER),
    /** Refresh token đã thu hồi bị gửi lại (có người giữ bản sao): cả họ token bị thu hồi. */
    REFRESH_TOKEN_REUSE_DETECTED(Target.USER);

    private final Target target;

    AuditAction(Target target) {
        this.target = target;
    }

    public Target target() {
        return target;
    }

    public enum Target {
        ORGANIZER, USER
    }
}
