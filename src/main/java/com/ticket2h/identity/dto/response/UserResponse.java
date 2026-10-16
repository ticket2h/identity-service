package com.ticket2h.identity.dto.response;

import java.util.UUID;

import com.ticket2h.identity.entity.Role;
import com.ticket2h.identity.entity.User;

/**
 * Tài khoản trả ra API, cả công khai lẫn nội bộ. Không có {@code passwordHash}. {@code organizerId} chỉ có với
 * ORGANIZER (null với vai trò khác). Dạng JSON này là hợp đồng với service khác: infra/docs/contracts.md.
 */
public record UserResponse(UUID id, String email, String fullName, Role role, UUID organizerId) {

    public static UserResponse of(User user) {
        return new UserResponse(user.getId(), user.getEmail(), user.getFullName(), user.getRole(),
                user.getOrganizerId());
    }
}
