package com.ticket2h.identity.support;

import java.time.Instant;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import tools.jackson.databind.json.JsonMapper;

/**
 * Lớp cha cho integration test: app chạy ở cổng ngẫu nhiên với PostgreSQL thật (Testcontainers). Dọn bảng bằng
 * TRUNCATE trước mỗi test. Khoá ký JWT sinh lúc chạy ({@link TestKeys}), khoá API nội bộ ở application-test.yml.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import({TestcontainersConfig.class, TestData.class})
@ActiveProfiles("test")
public abstract class IntegrationTest {

    public static final Instant START = Instant.parse("2026-11-01T12:00:00Z");

    /** Phải giống ticket2h.internal-api.clients trong application-test.yml. */
    public static final String CORE_KEY = "test-internal-key-core-0123456789abcdef";
    public static final String NOTIFICATION_KEY = "test-internal-key-notification-0123456789";

    @LocalServerPort
    protected int port;

    @Autowired protected JdbcTemplate jdbc;
    @Autowired protected MutableClock clock;
    @Autowired protected TestData testData;
    @Autowired protected JsonMapper json;

    protected Api api;

    @DynamicPropertySource
    static void testProperties(DynamicPropertyRegistry registry) {
        registry.add("ticket2h.jwt.private-key", () -> TestKeys.privateBase64(TestKeys.JWT));
    }

    @BeforeEach
    void resetState() {
        jdbc.execute("TRUNCATE refresh_tokens, users, organizers");
        clock.set(START);
        api = new Api(port, json);
    }
}
