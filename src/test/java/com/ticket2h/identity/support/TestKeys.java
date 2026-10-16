package com.ticket2h.identity.support;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;

/** Khoá RSA sinh lúc chạy test để ký JWT. Không có khoá nào được commit. */
public final class TestKeys {

    /** Khoá ký của service trong test (cấu hình qua {@code ticket2h.jwt.private-key}). */
    public static final KeyPair JWT = rsa();

    public static KeyPair rsa() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            return generator.generateKeyPair();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Khoá riêng dạng base64 của PKCS#8 DER, đúng định dạng của biến JWT_PRIVATE_KEY. */
    public static String privateBase64(KeyPair pair) {
        return Base64.getEncoder().encodeToString(pair.getPrivate().getEncoded());
    }

    private TestKeys() {}
}
