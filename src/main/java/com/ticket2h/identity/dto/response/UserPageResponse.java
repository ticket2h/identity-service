package com.ticket2h.identity.dto.response;

import java.util.List;

/**
 * Một trang tài khoản (GET /admin/users), phân trang keyset. {@code limit} là cỡ trang thật sự đã dùng (sau khi giới
 * hạn). Trang sau: gửi lại {@code nextCursor} trong tham số {@code cursor}; {@code nextCursor} null là hết danh sách.
 * Không có tổng số tài khoản: đếm 2 triệu dòng ở mỗi trang tốn hơn chính việc lấy trang đó.
 */
public record UserPageResponse(List<UserResponse> items, int limit, String nextCursor) {}
