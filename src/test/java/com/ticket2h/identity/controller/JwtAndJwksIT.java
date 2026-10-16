package com.ticket2h.identity.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;

import javax.crypto.spec.SecretKeySpec;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import com.nimbusds.jwt.SignedJWT;
import com.ticket2h.identity.entity.Role;
import com.ticket2h.identity.support.Api;
import com.ticket2h.identity.support.IntegrationTest;
import com.ticket2h.identity.support.TestData;
import com.ticket2h.identity.support.TestKeys;

/**
 * JWT RS256 và JWKS: hợp đồng với mọi service kiểm tra token (infra/docs/contracts.md). Các test ở đây kiểm tra
 * token y như core-service sẽ làm: chỉ dựa vào khoá công khai lấy từ {@code /.well-known/jwks.json}.
 */
class JwtAndJwksIT extends IntegrationTest {

    @Test
    void customer_token_is_rs256_with_kid_and_has_no_organizer_claim() throws Exception {
        testData.userWithEmail("c@test.local", Role.CUSTOMER, null);

        String token = api.login("c@test.local", TestData.PASSWORD);

        JWSHeader header = SignedJWT.parse(token).getHeader();
        assertThat(header.getAlgorithm().getName()).isEqualTo("RS256");
        assertThat(header.getKeyID()).isEqualTo("k1");
        Jwt jwt = decoderFromJwks().decode(token);
        // iss là chuỗi "ticket2h", không phải URL: dùng getClaimAsString, Jwt.getIssuer() sẽ ném lỗi
        assertThat(jwt.getClaimAsString("iss")).isEqualTo("ticket2h");
        assertThat(jwt.getSubject()).isEqualTo(api.get("/me", token).json().get("id").asString());
        assertThat(jwt.getClaimAsString("role")).isEqualTo("CUSTOMER");
        assertThat(jwt.hasClaim("organizerId")).isFalse();
        assertThat(Duration.between(jwt.getIssuedAt(), jwt.getExpiresAt())).isEqualTo(Duration.ofHours(1));
    }

    @Test
    void organizer_token_carries_the_organizer_id() {
        TestData.OrganizerRef organizer = testData.organizer();

        Jwt jwt = decoderFromJwks().decode(api.login(organizer.email(), TestData.PASSWORD));

        assertThat(jwt.getClaimAsString("role")).isEqualTo("ORGANIZER");
        assertThat(jwt.getClaimAsString("organizerId")).isEqualTo(organizer.organizerId().toString());
    }

    @Test
    void jwks_has_only_public_key_material_and_is_cacheable() throws Exception {
        Api.Response response = api.get("/.well-known/jwks.json", null);

        assertThat(response.status()).isEqualTo(200);
        assertThat(response.header("Cache-Control")).contains("max-age=300").contains("public");
        Map<String, Object> key = JWKSet.parse(response.body()).getKeys().getFirst().toJSONObject();
        assertThat(key).containsKeys("kty", "kid", "use", "alg", "n", "e")
                .doesNotContainKeys("d", "p", "q", "dp", "dq", "qi");
        assertThat(key.get("kid")).isEqualTo("k1");
        assertThat(key.get("alg")).isEqualTo("RS256");
        // Đúng khoá của service: modulus khớp với khoá test
        RSAKey published = RSAKey.parse(key);
        assertThat(published.toRSAPublicKey().getModulus())
                .isEqualTo(((RSAPublicKey) TestKeys.JWT.getPublic()).getModulus());
    }

    @Test
    void token_signed_by_another_key_is_rejected_even_with_the_same_kid() {
        UUID user = testData.user();
        KeyPair attacker = TestKeys.rsa();
        RSAKey attackerKey = new RSAKey.Builder((RSAPublicKey) attacker.getPublic())
                .privateKey((RSAPrivateKey) attacker.getPrivate()).keyID("k1").build();
        String forged = new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(attackerKey)))
                .encode(JwtEncoderParameters.from(JwsHeader.with(SignatureAlgorithm.RS256).keyId("k1").build(),
                        claims(user, "ADMIN")))
                .getTokenValue();

        assertThat(api.get("/me", forged).status()).isEqualTo(401);
        assertThatThrownBy(() -> decoderFromJwks().decode(forged)).isInstanceOf(JwtException.class);
    }

    /** Lỗi "alg confusion": token HS256 dùng khoá công khai làm secret không được chấp nhận. */
    @Test
    void hs256_token_is_rejected() {
        UUID user = testData.user();
        byte[] secret = TestKeys.JWT.getPublic().getEncoded();
        String hs256 = new NimbusJwtEncoder(new ImmutableSecret<>(new SecretKeySpec(secret, "HmacSHA256")))
                .encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims(user, "ADMIN")))
                .getTokenValue();

        assertThat(api.get("/me", hs256).status()).isEqualTo(401);
        assertThat(new String(java.util.Base64.getUrlDecoder().decode(hs256.split("\\.")[0]),
                StandardCharsets.UTF_8)).contains("HS256");
    }

    /** Bộ giải mã giống hệt cách core-service cấu hình: chỉ biết URL của JWKS. */
    private JwtDecoder decoderFromJwks() {
        return NimbusJwtDecoder.withJwkSetUri("http://localhost:" + port + "/.well-known/jwks.json")
                .jwsAlgorithm(SignatureAlgorithm.RS256)
                .build();
    }

    private JwtClaimsSet claims(UUID user, String role) {
        return JwtClaimsSet.builder()
                .issuer("ticket2h")
                .subject(user.toString())
                .issuedAt(clock.instant())
                .expiresAt(clock.instant().plus(Duration.ofHours(1)))
                .claim("role", role)
                .build();
    }
}
