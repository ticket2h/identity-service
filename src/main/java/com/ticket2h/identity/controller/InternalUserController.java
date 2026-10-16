package com.ticket2h.identity.controller;

import java.util.UUID;

import jakarta.validation.Valid;

import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ticket2h.identity.dto.request.UserLookupRequest;
import com.ticket2h.identity.dto.response.UserResponse;
import com.ticket2h.identity.exception.NotFoundException;
import com.ticket2h.identity.service.AccountService;

/**
 * API nội bộ cho service khác: notification-service lấy email người nhận, core-service tìm người nhận vé và
 * nhân viên soát vé theo email. Chỉ service có khoá {@code X-Internal-Api-Key} gọi được (SecurityConfig).
 * Hợp đồng: infra/docs/contracts.md.
 */
@RestController
@RequestMapping("/internal/users")
class InternalUserController {

    private final AccountService accounts;

    InternalUserController(AccountService accounts) {
        this.accounts = accounts;
    }

    @GetMapping("/{userId}")
    ResponseEntity<UserResponse> byId(@PathVariable UUID userId) {
        return ok(accounts.find(userId).map(UserResponse::of).orElseThrow(() -> new NotFoundException("người dùng")));
    }

    /**
     * Tìm theo email bằng POST thay vì {@code GET ?email=}: email là dữ liệu cá nhân, nằm trong body thì không
     * lọt vào URL, access log hay log của proxy.
     */
    @PostMapping("/lookup")
    ResponseEntity<UserResponse> byEmail(@Valid @RequestBody UserLookupRequest request) {
        return ok(accounts.findByEmail(request.email()).map(UserResponse::of)
                .orElseThrow(() -> new NotFoundException("người dùng")));
    }

    private static ResponseEntity<UserResponse> ok(UserResponse body) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(body);
    }
}
