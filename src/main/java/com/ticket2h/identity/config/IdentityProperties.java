package com.ticket2h.identity.config;

import java.time.Duration;
import java.util.Map;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** Toàn bộ cấu hình {@code ticket2h.*} trong application.yml. */
@Validated
@ConfigurationProperties("ticket2h")
public record IdentityProperties(@Valid @NotNull Jwt jwt, @Valid @NotNull InternalApi internalApi) {

    /**
     * {@code privateKey}: khoá riêng RSA (PKCS#8 DER, base64) để ký JWT. Khoá công khai tính ra từ khoá riêng và
     * công bố ở {@code /.well-known/jwks.json} với {@code keyId}.
     */
    public record Jwt(
            @NotBlank String issuer,
            @NotNull Duration ttl,
            @NotBlank String keyId,
            @NotBlank String privateKey) {}

    /** Khoá của từng service được gọi {@code /internal/**}: tên service → khoá (tối thiểu 32 ký tự). */
    public record InternalApi(
            @NotEmpty Map<String, @NotBlank @Size(min = 32, message = "phải dài ít nhất 32 ký tự") String> clients) {}
}
