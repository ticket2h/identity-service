package com.ticket2h.identity.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.ticket2h.identity.entity.User;

/** {@link UserSearch}: danh sách cho trang quản trị, phân trang keyset (tự viết SQL ở {@link UserSearchImpl}). */
public interface UserRepository extends JpaRepository<User, UUID>, UserSearch {

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);
}
