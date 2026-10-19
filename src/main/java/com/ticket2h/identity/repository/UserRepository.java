package com.ticket2h.identity.repository;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.ticket2h.identity.entity.Role;
import com.ticket2h.identity.entity.User;

public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    /**
     * Tìm tài khoản cho trang quản trị, phân trang ngay trong database (LIMIT/OFFSET, kèm một câu đếm tổng).
     * {@code pattern} là mẫu LIKE đã escape bằng {@code \}, so khớp với email hoặc họ tên, không phân biệt hoa
     * thường; cả hai vế cùng qua {@code LOWER} của PostgreSQL nên chữ có dấu cũng khớp. Email đã lưu chữ thường.
     * Không lọc vai trò thì truyền đủ mọi {@link Role} (tránh tham số null trong câu truy vấn).
     * Mới tạo trước; tạo cùng lúc thì theo email để phân trang không bị lặp hay sót.
     */
    @Query("""
            SELECT u FROM User u
            WHERE u.role IN :roles
              AND (u.email LIKE LOWER(:pattern) ESCAPE '\\' OR LOWER(u.fullName) LIKE LOWER(:pattern) ESCAPE '\\')
            ORDER BY u.createdAt DESC, u.email
            """)
    Page<User> search(Collection<Role> roles, String pattern, Pageable pageable);
}
