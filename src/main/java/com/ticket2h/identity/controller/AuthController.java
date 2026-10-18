package com.ticket2h.identity.controller;

import java.time.Duration;

import jakarta.validation.Valid;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.ticket2h.identity.config.IdentityProperties;
import com.ticket2h.identity.dto.request.LoginRequest;
import com.ticket2h.identity.dto.request.RegisterRequest;
import com.ticket2h.identity.dto.response.LoginResponse;
import com.ticket2h.identity.dto.response.UserResponse;
import com.ticket2h.identity.entity.User;
import com.ticket2h.identity.exception.ErrorCode;
import com.ticket2h.identity.exception.ProblemDetails;
import com.ticket2h.identity.security.AuthUser;
import com.ticket2h.identity.service.AccountService;
import com.ticket2h.identity.service.RefreshTokenService;
import com.ticket2h.identity.service.TokenService;

@RestController
class AuthController {

    /** Cookie chứa refresh token. httpOnly: JavaScript của trang không đọc được. */
    static final String REFRESH_COOKIE = "ticket2h_refresh";

    private final AccountService accounts;
    private final RefreshTokenService refreshTokens;
    private final TokenService tokens;
    private final IdentityProperties properties;

    AuthController(AccountService accounts, RefreshTokenService refreshTokens, TokenService tokens,
            IdentityProperties properties) {
        this.accounts = accounts;
        this.refreshTokens = refreshTokens;
        this.tokens = tokens;
        this.properties = properties;
    }

    @PostMapping("/auth/register")
    @ResponseStatus(HttpStatus.CREATED)
    UserResponse register(@Valid @RequestBody RegisterRequest request) {
        return UserResponse.of(accounts.register(request.email(), request.password(), request.fullName()));
    }

    /** Access token trả trong body (như trước); refresh token đặt vào cookie httpOnly. */
    @PostMapping("/auth/login")
    ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        AccountService.LoginResult result = accounts.login(request.email(), request.password());
        String refreshToken = refreshTokens.issue(result.user());
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, refreshCookie(refreshToken, properties.refreshToken().ttl()))
                .body(new LoginResponse(result.accessToken(), "Bearer", result.expiresInSeconds(),
                        UserResponse.of(result.user())));
    }

    /**
     * Đổi refresh token trong cookie lấy access token mới và refresh token mới. Token không hợp lệ thì 401 và xoá
     * cookie, trình duyệt phải đăng nhập lại.
     */
    @PostMapping("/auth/refresh")
    ResponseEntity<?> refresh(@CookieValue(name = REFRESH_COOKIE, required = false) String refreshToken) {
        RefreshTokenService.Rotated rotated = refreshToken == null ? null
                : refreshTokens.rotate(refreshToken).orElse(null);
        if (rotated == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .header(HttpHeaders.SET_COOKIE, refreshCookie("", Duration.ZERO))
                    .body(ProblemDetails.of(ErrorCode.UNAUTHENTICATED,
                            "Phiên đăng nhập đã hết hạn, hãy đăng nhập lại"));
        }
        User user = rotated.user();
        TokenService.IssuedToken access = tokens.issue(user);
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, refreshCookie(rotated.refreshToken(), properties.refreshToken().ttl()))
                .body(new LoginResponse(access.value(), "Bearer", access.expiresInSeconds(), UserResponse.of(user)));
    }

    /** Thu hồi refresh token (cả family) và xoá cookie. Access token đã phát vẫn sống tới khi hết hạn. */
    @PostMapping("/auth/logout")
    ResponseEntity<Void> logout(@CookieValue(name = REFRESH_COOKIE, required = false) String refreshToken) {
        if (refreshToken != null) {
            refreshTokens.revoke(refreshToken);
        }
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, refreshCookie("", Duration.ZERO))
                .build();
    }

    @GetMapping("/me")
    UserResponse me(AuthUser caller) {
        return UserResponse.of(accounts.get(caller.id()));
    }

    private String refreshCookie(String value, Duration maxAge) {
        return ResponseCookie.from(REFRESH_COOKIE, value)
                .httpOnly(true)
                .secure(properties.refreshToken().cookieSecure())
                .sameSite("Strict")
                .path(properties.refreshToken().cookiePath())
                .maxAge(maxAge)
                .build()
                .toString();
    }
}
