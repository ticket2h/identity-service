package com.ticket2h.identity.dto.request;

import java.util.List;
import java.util.UUID;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Tra nhiều tài khoản theo id trong một lần gọi (API nội bộ). Tối đa {@value #MAX_IDS} id: core-service xuất danh sách
 * 500.000 khách bằng 500 lần gọi thay vì 500.000 lần. id trùng nhau được coi như một.
 */
public record UserBatchRequest(@NotNull @NotEmpty @Size(max = UserBatchRequest.MAX_IDS) List<@NotNull UUID> ids) {

    public static final int MAX_IDS = 1_000;
}
