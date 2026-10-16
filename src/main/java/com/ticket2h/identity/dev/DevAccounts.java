package com.ticket2h.identity.dev;

import java.util.UUID;

/**
 * Id CỐ ĐỊNH của dữ liệu mẫu (profile dev). core-service tạo sự kiện mẫu cho {@link #ORGANIZER_SKY} và phân
 * công {@link #STAFF1} soát vé bằng đúng các id này, nên hai bên phải khớp (infra/docs/contracts.md).
 */
public final class DevAccounts {

    public static final String PASSWORD = "Passw0rd!";

    /** Ban tổ chức "Sky Music Entertainment". */
    public static final UUID ORGANIZER_SKY = UUID.fromString("0a000000-0000-4000-8000-000000000001");

    public static final UUID ADMIN = UUID.fromString("0b000000-0000-4000-8000-000000000001");
    public static final UUID ORGANIZER1 = UUID.fromString("0b000000-0000-4000-8000-000000000002");
    public static final UUID STAFF1 = UUID.fromString("0b000000-0000-4000-8000-000000000003");
    public static final UUID BUYER1 = UUID.fromString("0b000000-0000-4000-8000-000000000004");
    public static final UUID BUYER2 = UUID.fromString("0b000000-0000-4000-8000-000000000005");
    public static final UUID BUYER3 = UUID.fromString("0b000000-0000-4000-8000-000000000006");

    private DevAccounts() {}
}
