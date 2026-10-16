package com.ticket2h.identity.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Tìm tài khoản theo email (API nội bộ). Không đặt {@code @Email} ở đây: email được chuẩn hoá (bỏ khoảng trắng,
 * chữ thường) rồi mới kiểm tra dạng, nên " Buyer@Test.Local " vẫn tìm được.
 */
public record UserLookupRequest(@NotBlank @Size(max = 320) String email) {}
