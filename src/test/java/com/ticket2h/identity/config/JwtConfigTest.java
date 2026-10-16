package com.ticket2h.identity.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Base64;

import org.junit.jupiter.api.Test;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.RSAKey;
import com.ticket2h.identity.support.TestKeys;

class JwtConfigTest {

    @Test
    void valid_rsa_key_gives_a_jwk_with_kid_and_derived_public_key() throws Exception {
        RSAKey key = JwtConfig.rsaKey(TestKeys.privateBase64(TestKeys.JWT), "k1");

        assertThat(key.getKeyID()).isEqualTo("k1");
        assertThat(key.getAlgorithm()).isEqualTo(JWSAlgorithm.RS256);
        assertThat(key.getKeyUse()).isEqualTo(KeyUse.SIGNATURE);
        assertThat(key.toPublicKey().getEncoded()).isEqualTo(TestKeys.JWT.getPublic().getEncoded());
        assertThat(key.toPublicJWK().isPrivate()).isFalse();
    }

    @Test
    void invalid_base64_fails_without_echoing_the_value() {
        String value = "khong-phai-base64!!!";

        assertThatThrownBy(() -> JwtConfig.rsaKey(value, "k1"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("base64")
                .hasMessageNotContaining(value)
                .hasNoCause();
    }

    @Test
    void non_rsa_key_is_rejected_without_echoing_the_value() throws Exception {
        String ed25519 = Base64.getEncoder().encodeToString(
                KeyPairGenerator.getInstance("Ed25519").generateKeyPair().getPrivate().getEncoded());

        assertThatThrownBy(() -> JwtConfig.rsaKey(ed25519, "k1"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("RSA")
                .hasMessageNotContaining(ed25519);
    }

    @Test
    void rsa_key_shorter_than_2048_bits_is_rejected() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(1024);
        KeyPair weak = generator.generateKeyPair();

        assertThatThrownBy(() -> JwtConfig.rsaKey(TestKeys.privateBase64(weak), "k1"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("2048");
    }
}
