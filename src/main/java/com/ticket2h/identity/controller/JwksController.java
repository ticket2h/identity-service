package com.ticket2h.identity.controller;

import java.time.Duration;
import java.util.Map;

import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.nimbusds.jose.jwk.JWKSet;

/**
 * Khoá công khai để service khác kiểm tra JWT (JWKS, RFC 7517). Chỉ có phần công khai (n, e, kid...), không có
 * khoá riêng. Cho cache 5 phút: bên kiểm tra không phải hỏi lại mỗi request; khi đổi khoá thì công bố khoá mới
 * cạnh khoá cũ ít nhất 5 phút + thời hạn token rồi mới gỡ khoá cũ.
 */
@RestController
class JwksController {

    private final JWKSet publicJwkSet;

    JwksController(JWKSet publicJwkSet) {
        this.publicJwkSet = publicJwkSet;
    }

    @GetMapping("/.well-known/jwks.json")
    ResponseEntity<Map<String, Object>> jwks() {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(Duration.ofMinutes(5)).cachePublic())
                .body(publicJwkSet.toJSONObject());
    }
}
