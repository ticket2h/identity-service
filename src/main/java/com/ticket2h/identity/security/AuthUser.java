package com.ticket2h.identity.security;

import java.util.UUID;

import com.ticket2h.identity.entity.Role;

/** Người đang gọi API, lấy từ JWT. Khai báo làm tham số controller để Spring tự điền. */
public record AuthUser(UUID id, Role role, UUID organizerId) {}
