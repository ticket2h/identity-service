package com.ticket2h.identity.dto.request;

import java.util.UUID;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import com.ticket2h.identity.entity.Role;

/** Admin tạo tài khoản. {@code organizerId} bắt buộc với ORGANIZER, phải bỏ trống với vai trò khác. */
public record CreateUserRequest(
        @NotBlank @Email @Size(max = 320) String email,
        @NotBlank @Size(min = 8, max = 100) String password,
        @NotBlank @Size(max = 200) String fullName,
        @NotNull Role role,
        UUID organizerId) {}
