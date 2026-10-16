package com.ticket2h.identity.config;

import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.interfaces.RSAPrivateCrtKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.RSAPublicKeySpec;
import java.time.Clock;
import java.time.Duration;
import java.util.Base64;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;

/**
 * JWT ký RS256 (khoá bất đối xứng). Chỉ service này giữ khoá riêng để KÝ; service khác lấy khoá công khai ở
 * {@code /.well-known/jwks.json} để KIỂM TRA. Lộ khoá công khai không sao; ai có khoá riêng thì tự phát được
 * token ADMIN, nên khoá riêng chỉ nằm trong biến môi trường của service này và không bao giờ ghi log.
 */
@Configuration(proxyBeanMethods = false)
public class JwtConfig {

    /** RSA dưới 2048 bit coi như không an toàn. */
    static final int MIN_KEY_BITS = 2048;

    /** Cặp khoá (riêng + công khai) dạng JWK, có {@code kid} để bên kiểm tra chọn đúng khoá. */
    @Bean
    RSAKey signingKey(IdentityProperties properties) {
        return rsaKey(properties.jwt().privateKey(), properties.jwt().keyId());
    }

    /** Chỉ phần công khai (n, e, kid...), cho JwksController. */
    @Bean
    JWKSet publicJwkSet(RSAKey signingKey) {
        return new JWKSet(signingKey.toPublicJWK());
    }

    @Bean
    JwtEncoder jwtEncoder(RSAKey signingKey) {
        return new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(signingKey)));
    }

    /** Kiểm tra token cho chính service này (GET /me). Dùng thẳng khoá công khai, không cần gọi JWKS của mình. */
    @Bean
    JwtDecoder jwtDecoder(RSAKey signingKey, IdentityProperties properties, Clock clock) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withPublicKey(publicKey(signingKey))
                .signatureAlgorithm(SignatureAlgorithm.RS256)
                .build();
        // Kiểm tra hạn token theo cùng Clock với phần còn lại của ứng dụng (test dùng MutableClock)
        JwtTimestampValidator timestamps = new JwtTimestampValidator(Duration.ofSeconds(30));
        timestamps.setClock(clock);
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                timestamps, new JwtIssuerValidator(properties.jwt().issuer())));
        return decoder;
    }

    /**
     * Đọc khoá riêng RSA từ base64 của PKCS#8 DER, tính khoá công khai từ nó. Thông báo lỗi KHÔNG chứa nội dung
     * khoá (lỗi khởi động hay được dán lên chat, issue...).
     */
    static RSAKey rsaKey(String base64Pkcs8, String keyId) {
        RSAPrivateCrtKey privateKey = parsePrivateKey(base64Pkcs8);
        RSAPublicKey publicKey;
        try {
            publicKey = (RSAPublicKey) KeyFactory.getInstance("RSA").generatePublic(
                    new RSAPublicKeySpec(privateKey.getModulus(), privateKey.getPublicExponent()));
        } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
            throw new IllegalStateException("Không tính được khoá công khai từ ticket2h.jwt.private-key");
        }
        return new RSAKey.Builder(publicKey)
                .privateKey(privateKey)
                .keyID(keyId)
                .keyUse(KeyUse.SIGNATURE)
                .algorithm(JWSAlgorithm.RS256)
                .build();
    }

    static RSAPrivateCrtKey parsePrivateKey(String base64Pkcs8) {
        byte[] der;
        try {
            der = Base64.getDecoder().decode(base64Pkcs8.strip());
        } catch (IllegalArgumentException e) {
            // Không gắn exception gốc: thông báo của nó có thể chứa một đoạn của chuỗi khoá
            throw new IllegalStateException("ticket2h.jwt.private-key không phải base64 hợp lệ");
        }
        PrivateKey key;
        try {
            key = KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(der));
        } catch (InvalidKeySpecException e) {
            throw new IllegalStateException(
                    "ticket2h.jwt.private-key không phải khoá riêng RSA dạng PKCS#8 DER (xem infra/scripts/init-env.sh)");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
        if (!(key instanceof RSAPrivateCrtKey crt)) {
            throw new IllegalStateException("ticket2h.jwt.private-key phải là khoá RSA có đủ tham số CRT (PKCS#8)");
        }
        if (crt.getModulus().bitLength() < MIN_KEY_BITS) {
            throw new IllegalStateException("ticket2h.jwt.private-key phải dài ít nhất " + MIN_KEY_BITS + " bit");
        }
        return crt;
    }

    private static RSAPublicKey publicKey(RSAKey key) {
        try {
            return key.toRSAPublicKey();
        } catch (JOSEException e) {
            throw new IllegalStateException("Khoá JWT không hợp lệ", e);
        }
    }
}
