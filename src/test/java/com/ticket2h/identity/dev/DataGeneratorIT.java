package com.ticket2h.identity.dev;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import javax.sql.DataSource;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.ticket2h.identity.entity.Role;
import com.ticket2h.identity.support.Api;
import com.ticket2h.identity.support.IntegrationTest;

/** Công cụ sinh tài khoản (ADR-021), ở quy mô nhỏ. */
class DataGeneratorIT extends IntegrationTest {

    @Autowired DataSource dataSource;
    @Autowired PasswordEncoder passwords;
    @Autowired ConfigurableApplicationContext context;

    @Test
    void generates_accounts_with_the_shared_ids_that_can_log_in() throws Exception {
        DataGenerator generator = new DataGenerator(new DatagenProperties(2_000, 3, 5), dataSource, jdbc, passwords,
                clock, context);

        generator.generate();

        assertThat(jdbc.queryForObject("SELECT count(*) FROM users WHERE role = 'CUSTOMER'", Integer.class))
                .isEqualTo(2_000);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM users WHERE role = 'STAFF'", Integer.class)).isEqualTo(5);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM organizers", Integer.class)).isEqualTo(3);
        assertThat(jdbc.queryForObject("SELECT organizer_id FROM users WHERE id = ?", java.util.UUID.class,
                DatagenIds.organizerAccount(2))).isEqualTo(DatagenIds.organizer(2));
        assertThat(jdbc.queryForObject("SELECT email FROM users WHERE id = ?", String.class, DatagenIds.customer(42)))
                .isEqualTo("khach42@datagen.ticket2h.local");
        // Khách đăng ký muộn hơn thì đứng trước trong danh sách quản trị
        assertThat(jdbc.queryForObject("SELECT id FROM users WHERE role = 'CUSTOMER' ORDER BY created_at DESC, id DESC "
                + "LIMIT 1", java.util.UUID.class)).isEqualTo(DatagenIds.customer(2_000));

        Api.Response login = api.post("/auth/login", null,
                Map.of("email", "khach7@datagen.ticket2h.local", "password", DevAccounts.PASSWORD));
        assertThat(login.status()).isEqualTo(200);
        Api.Response staffLogin = api.post("/auth/login", null,
                Map.of("email", "soatve3@datagen.ticket2h.local", "password", DevAccounts.PASSWORD));
        assertThat(staffLogin.json().get("user").get("role").asString()).isEqualTo(Role.STAFF.name());

        generator.generate();   // lần hai: đã có, không sinh chồng

        assertThat(jdbc.queryForObject("SELECT count(*) FROM users", Integer.class)).isEqualTo(2_000 + 5 + 3);
    }

    @Test
    void names_are_vietnamese_and_stable() {
        assertThat(DataGenerator.name(1)).isEqualTo(DataGenerator.name(1)).matches("\\p{L}+ \\p{L}+ \\p{L}+");
        assertThat(DataGenerator.name(1)).isNotEqualTo(DataGenerator.name(2));
    }
}
