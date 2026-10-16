package com.ticket2h.identity.dev;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import com.ticket2h.identity.support.TestKeys;
import com.ticket2h.identity.support.TestcontainersConfig;

/**
 * Dữ liệu mẫu có đúng id cố định mà core-service dùng để tạo sự kiện mẫu. Không kế thừa IntegrationTest vì lớp đó
 * TRUNCATE bảng trước mỗi test, xoá mất dữ liệu seeder vừa tạo lúc khởi động.
 */
@SpringBootTest
@Import(TestcontainersConfig.class)
@ActiveProfiles({"test", "dev"})
class DevDataSeederIT {

    @Autowired JdbcTemplate jdbc;

    @DynamicPropertySource
    static void testProperties(DynamicPropertyRegistry registry) {
        registry.add("ticket2h.jwt.private-key", () -> TestKeys.privateBase64(TestKeys.JWT));
    }

    @Test
    void sample_accounts_have_the_fixed_ids_core_service_relies_on() {
        assertThat(idOf("staff1@test.local")).isEqualTo(DevAccounts.STAFF1);
        assertThat(idOf("organizer1@test.local")).isEqualTo(DevAccounts.ORGANIZER1);
        assertThat(jdbc.queryForObject("SELECT organizer_id FROM users WHERE id = ?", UUID.class, DevAccounts.ORGANIZER1))
                .isEqualTo(DevAccounts.ORGANIZER_SKY);
        assertThat(jdbc.queryForObject("SELECT name FROM organizers WHERE id = ?", String.class, DevAccounts.ORGANIZER_SKY))
                .isEqualTo("Sky Music Entertainment");
        assertThat(idOf("buyer3@test.local")).isEqualTo(DevAccounts.BUYER3);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM users", Integer.class)).isEqualTo(6);   // admin, organizer1, staff1, buyer1..3
    }

    private UUID idOf(String email) {
        return jdbc.queryForObject("SELECT id FROM users WHERE email = ?", UUID.class, email);
    }
}
