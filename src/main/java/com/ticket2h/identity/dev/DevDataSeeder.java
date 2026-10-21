package com.ticket2h.identity.dev;

import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import com.ticket2h.identity.entity.Role;
import com.ticket2h.identity.service.AccountService;

/**
 * Tài khoản mẫu cho chạy thử local (profile {@code dev}), id cố định theo {@link DevAccounts}. Chỉ tạo khi chưa có
 * admin@test.local. Mọi tài khoản dùng mật khẩu {@value DevAccounts#PASSWORD}.
 */
@Component
@Profile("dev")
@Order(1)   // trước công cụ sinh tài khoản (profile datagen) nếu bật cả hai
class DevDataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DevDataSeeder.class);

    private final AccountService accounts;

    DevDataSeeder(AccountService accounts) {
        this.accounts = accounts;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (accounts.emailExists("admin@test.local")) {
            log.info("Tài khoản mẫu đã có, bỏ qua");
            return;
        }
        String password = DevAccounts.PASSWORD;
        accounts.createUser(DevAccounts.ADMIN, "admin@test.local", password, "Quản trị viên", Role.ADMIN, null);
        accounts.createOrganizer(DevAccounts.ORGANIZER_SKY, "Sky Music Entertainment");
        accounts.createUser(DevAccounts.ORGANIZER1, "organizer1@test.local", password, "Ban tổ chức Sky",
                Role.ORGANIZER, DevAccounts.ORGANIZER_SKY);
        accounts.createUser(DevAccounts.STAFF1, "staff1@test.local", password, "Nhân viên soát vé 1", Role.STAFF,
                null);
        List<UUID> buyers = List.of(DevAccounts.BUYER1, DevAccounts.BUYER2, DevAccounts.BUYER3);
        for (int i = 0; i < buyers.size(); i++) {
            accounts.createUser(buyers.get(i), "buyer" + (i + 1) + "@test.local", password, "Khách hàng " + (i + 1),
                    Role.CUSTOMER, null);
        }

        log.info("""
                Đã tạo tài khoản mẫu (mật khẩu {}):
                  admin@test.local, organizer1@test.local (ban tổ chức {}), staff1@test.local ({}), buyer1..3@test.local""",
                password, DevAccounts.ORGANIZER_SKY, DevAccounts.STAFF1);
    }
}
