package com.ticket2h.identity.service;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Base64;
import java.util.UUID;

import com.ticket2h.identity.exception.ValidationException;

/**
 * Vị trí trong một danh sách phân trang keyset sắp theo thời gian rồi tới id: thời gian ({@code created_at} của tài
 * khoản, {@code occurred_at} của lịch sử thao tác) và {@code id} của dòng cuối trang trước. Gửi cho client dạng chuỗi
 * mờ (base64url), client chỉ gửi lại nguyên văn, không cần hiểu bên trong. Không phải bí mật: sửa tay thì chỉ nhảy tới
 * chỗ khác trong danh sách mà người đó vốn đã được xem.
 */
public record KeysetCursor(Instant at, UUID id) {

    public String encode() {
        String raw = at + "|" + id;
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    /** Chuỗi hỏng (không phải base64, sai dạng) thì 400 VALIDATION_FAILED. */
    public static KeysetCursor decode(String cursor) {
        try {
            String raw = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
            int separator = raw.indexOf('|');
            if (separator < 0) {
                throw new IllegalArgumentException("thiếu dấu |");
            }
            return new KeysetCursor(Instant.parse(raw.substring(0, separator)),
                    UUID.fromString(raw.substring(separator + 1)));
        } catch (IllegalArgumentException | DateTimeParseException e) {
            throw new ValidationException("cursor không hợp lệ");
        }
    }
}
