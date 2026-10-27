package com.ticket2h.identity.service;

import java.util.UUID;

import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import com.ticket2h.identity.security.JwtClaims;

/** Ai làm thao tác: người dùng (id, vai trò từ JWT) hoặc hệ thống (có tên). */
public record AuditActor(Type type, UUID id, String role, String name) {

    public enum Type { USER, SYSTEM }

    public static AuditActor system(String name) {
        return new AuditActor(Type.SYSTEM, null, null, name);
    }

    /** Người đang gọi API trên luồng hiện tại; không có (lúc khởi động, dữ liệu mẫu) thì {@code SYSTEM}. */
    public static AuditActor current() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication instanceof JwtAuthenticationToken token) {
            Jwt jwt = token.getToken();
            return new AuditActor(Type.USER, UUID.fromString(jwt.getSubject()), jwt.getClaimAsString(JwtClaims.ROLE),
                    null);
        }
        if (authentication != null && authentication.isAuthenticated()
                && !(authentication instanceof AnonymousAuthenticationToken)) {
            return system(authentication.getName());
        }
        return system("system");
    }
}
