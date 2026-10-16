package com.ticket2h.identity.security;

/** Tên claim riêng của Ticket2h trong JWT. Hợp đồng với các service kiểm tra token: infra/docs/contracts.md. */
public final class JwtClaims {

    /** Vai trò, ví dụ {@code "role": "CUSTOMER"}. */
    public static final String ROLE = "role";

    /** Ban tổ chức của tài khoản ORGANIZER (UUID dạng chuỗi). Vai trò khác không có claim này. */
    public static final String ORGANIZER_ID = "organizerId";

    private JwtClaims() {}
}
