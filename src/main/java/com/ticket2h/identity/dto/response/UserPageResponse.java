package com.ticket2h.identity.dto.response;

import java.util.List;

/**
 * Một trang tài khoản (GET /admin/users). Cùng dạng với trang sự kiện {@code GET /events} của core-service:
 * {@code page} đếm từ 0, {@code size} là cỡ trang thật sự đã dùng (sau khi giới hạn), {@code total} là tổng số tài
 * khoản khớp bộ lọc trên mọi trang.
 */
public record UserPageResponse(List<UserResponse> items, int page, int size, long total) {}
