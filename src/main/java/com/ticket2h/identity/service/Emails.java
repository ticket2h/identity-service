package com.ticket2h.identity.service;

import java.util.Locale;
import java.util.regex.Pattern;

public final class Emails {

    /** Kiểm tra thô sau khi chuẩn hoá: một ký tự @, hai bên không rỗng, không có khoảng trắng. */
    private static final Pattern SHAPE = Pattern.compile("[^@\\s]+@[^@\\s]+");

    /** Email lưu và so sánh ở dạng chữ thường, bỏ khoảng trắng hai đầu. */
    public static String normalize(String email) {
        return email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }

    public static boolean looksValid(String normalizedEmail) {
        return normalizedEmail != null && SHAPE.matcher(normalizedEmail).matches();
    }

    private Emails() {}
}
