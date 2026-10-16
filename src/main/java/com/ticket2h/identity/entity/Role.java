package com.ticket2h.identity.entity;

/** Vai trò người dùng. Mỗi tài khoản có đúng một vai trò. Tên vai trò nằm trong claim {@code role} của JWT. */
public enum Role {
    CUSTOMER, ORGANIZER, STAFF, ADMIN;

    public String authority() {
        return "ROLE_" + name();
    }
}
