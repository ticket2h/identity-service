package com.ticket2h.identity.repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;

import com.ticket2h.identity.entity.Role;
import com.ticket2h.identity.entity.User;

/**
 * Phân trang keyset bằng SQL thuần. Trang sau đi tiếp từ tài khoản cuối của trang trước:
 * {@code (created_at, id) < (:afterCreatedAt, :afterId)}. PostgreSQL dùng điều kiện này để nhảy thẳng tới đúng chỗ
 * trong index {@code idx_users_created}, nên trang thứ 10.000 nhanh như trang đầu. OFFSET thì phải đọc rồi bỏ qua
 * mọi dòng phía trước (trang 10.000 cỡ 20 là đọc 200.000 dòng).
 * Câu SQL ghép từ các đoạn viết sẵn; giá trị người dùng gửi lên chỉ đi qua tham số.
 */
class UserSearchImpl implements UserSearch {

    @PersistenceContext
    private EntityManager entityManager;

    @Override
    @SuppressWarnings("unchecked")
    public List<User> searchPage(Role role, String pattern, Instant afterCreatedAt, UUID afterId, int limit) {
        StringBuilder sql = new StringBuilder("SELECT * FROM users WHERE true");
        if (role != null) {
            sql.append(" AND role = :role");
        }
        if (pattern != null) {
            // Email đã lưu chữ thường; cả hai vế qua lower() của PostgreSQL nên chữ có dấu cũng khớp
            sql.append(" AND (email LIKE lower(:pattern) ESCAPE '\\'")
                    .append(" OR lower(full_name) LIKE lower(:pattern) ESCAPE '\\')");
        }
        if (afterCreatedAt != null) {
            sql.append(" AND (created_at, id) < (:afterCreatedAt, :afterId)");
        }
        sql.append(" ORDER BY created_at DESC, id DESC LIMIT :limit");

        Query query = entityManager.createNativeQuery(sql.toString(), User.class);
        if (role != null) {
            query.setParameter("role", role.name());
        }
        if (pattern != null) {
            query.setParameter("pattern", pattern);
        }
        if (afterCreatedAt != null) {
            query.setParameter("afterCreatedAt", afterCreatedAt);
            query.setParameter("afterId", afterId);
        }
        query.setParameter("limit", limit);
        return query.getResultList();
    }
}
