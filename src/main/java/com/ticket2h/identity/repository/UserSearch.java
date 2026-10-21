package com.ticket2h.identity.repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.ticket2h.identity.entity.Role;
import com.ticket2h.identity.entity.User;

/**
 * Phần tự viết của {@link UserRepository} (Spring Data gọi là "custom fragment"): Spring ghép method ở đây với các
 * method sinh tự động, cài đặt nằm ở {@link UserSearchImpl}.
 */
public interface UserSearch {

    /**
     * Một trang tài khoản, mới tạo trước, theo keyset: {@code afterCreatedAt}/{@code afterId} là tài khoản cuối của
     * trang trước (null ở trang đầu). {@code role} null: mọi vai trò. {@code pattern} null: không lọc; khác null là mẫu
     * LIKE đã escape bằng {@code \}, so với email hoặc họ tên không phân biệt hoa thường.
     */
    List<User> searchPage(Role role, String pattern, Instant afterCreatedAt, UUID afterId, int limit);
}
