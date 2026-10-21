package com.ticket2h.identity.dev;

import java.util.UUID;

/**
 * Id tất định của tài khoản sinh cho M4 (ADR-021 ở infra/docs/adr). core-service sinh đơn, vé cho đúng các id này mà
 * không đọc database của identity, nên công thức là hợp đồng giữa hai service (infra/docs/contracts.md, mục dữ liệu
 * sinh): đổi ở đây thì đổi cả {@code DatagenIds} của core-service. Cùng kiểu với id của {@link DevAccounts}.
 */
public final class DatagenIds {

    public static UUID organizer(long i) {
        return fixed("0e", i);
    }

    /** Tài khoản ORGANIZER của ban tổ chức thứ {@code i}. */
    public static UUID organizerAccount(long i) {
        return fixed("0c", i);
    }

    public static UUID customer(long i) {
        return fixed("0d", i);
    }

    public static UUID staff(long i) {
        return fixed("0f", i);
    }

    private static UUID fixed(String prefix, long i) {
        return UUID.fromString(prefix + "000000-0000-4000-8000-%012x".formatted(i));
    }

    private DatagenIds() {}
}
