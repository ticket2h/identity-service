package com.ticket2h.identity.exception;

import java.net.URI;
import java.util.Locale;

import org.springframework.http.HttpStatus;

/** Mã lỗi trả trong trường {@code code} của ProblemDetail. Cùng bộ mã và dạng {@code type} với core-service. */
public enum ErrorCode {

    VALIDATION_FAILED(HttpStatus.BAD_REQUEST, "Dữ liệu không hợp lệ"),
    UNAUTHENTICATED(HttpStatus.UNAUTHORIZED, "Chưa đăng nhập hoặc token không hợp lệ"),
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "Sai email hoặc mật khẩu"),
    FORBIDDEN(HttpStatus.FORBIDDEN, "Không có quyền"),
    NOT_FOUND(HttpStatus.NOT_FOUND, "Không tìm thấy"),
    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "Phương thức không được hỗ trợ"),
    EMAIL_ALREADY_REGISTERED(HttpStatus.CONFLICT, "Email đã được đăng ký"),
    PAYLOAD_TOO_LARGE(HttpStatus.CONTENT_TOO_LARGE, "Nội dung request quá lớn"),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "Lỗi hệ thống");

    private final HttpStatus status;
    private final String title;

    ErrorCode(HttpStatus status, String title) {
        this.status = status;
        this.title = title;
    }

    public HttpStatus status() {
        return status;
    }

    public String title() {
        return title;
    }

    /** Ví dụ EMAIL_ALREADY_REGISTERED → https://ticket2h.dev/errors/email-already-registered */
    public URI type() {
        return URI.create("https://ticket2h.dev/errors/" + name().toLowerCase(Locale.ROOT).replace('_', '-'));
    }
}
