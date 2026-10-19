package com.ticket2h.identity.support;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.ticket2h.identity.entity.Role;
import com.ticket2h.identity.service.AccountService;
import com.ticket2h.identity.service.TokenService;

/**
 * Tạo dữ liệu test bằng JDBC (nhanh, không đi qua API). Mọi tài khoản dùng mật khẩu {@link #PASSWORD}.
 * Token ký bằng đúng {@link TokenService} của app.
 */
public class TestData {

    public static final String PASSWORD = "Passw0rd!";

    private final JdbcTemplate jdbc;
    private final MutableClock clock;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokens;
    private final AccountService accounts;
    private final AtomicLong sequence = new AtomicLong();
    private volatile String passwordHash;

    public TestData(JdbcTemplate jdbc, MutableClock clock, PasswordEncoder passwordEncoder, TokenService tokens,
            AccountService accounts) {
        this.jdbc = jdbc;
        this.clock = clock;
        this.passwordEncoder = passwordEncoder;
        this.tokens = tokens;
        this.accounts = accounts;
    }

    public UUID user() {
        return user(Role.CUSTOMER);
    }

    public UUID user(Role role) {
        return userWithEmail("u" + sequence.incrementAndGet() + "@test.local", role, null);
    }

    public UUID userWithEmail(String email, Role role, UUID organizerId) {
        return insertUser(email, "Người dùng " + email, role, organizerId);
    }

    /** Tài khoản với họ tên cho trước (test tìm theo tên). {@code created_at} là giờ hiện tại của {@code clock}. */
    public UUID userNamed(String email, String fullName, Role role) {
        return insertUser(email, fullName, role, null);
    }

    private UUID insertUser(String email, String fullName, Role role, UUID organizerId) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO users (id, email, password_hash, full_name, role, organizer_id, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """, id, email, passwordHash(), fullName, role.name(), organizerId, now());
        return id;
    }

    /** Một ban tổ chức kèm một tài khoản ORGANIZER. */
    public OrganizerRef organizer() {
        UUID organizerId = UUID.randomUUID();
        jdbc.update("INSERT INTO organizers (id, name, created_at) VALUES (?, ?, ?)",
                organizerId, "BTC " + sequence.incrementAndGet(), now());
        String email = "org" + sequence.incrementAndGet() + "@test.local";
        return new OrganizerRef(organizerId, userWithEmail(email, Role.ORGANIZER, organizerId), email);
    }

    public String emailOf(UUID userId) {
        return jdbc.queryForObject("SELECT email FROM users WHERE id = ?", String.class, userId);
    }

    /** JWT của user, ký bằng TokenService của app (không cần gọi /auth/login). */
    public String token(UUID userId) {
        return tokens.issue(accounts.get(userId)).value();
    }

    private String passwordHash() {
        if (passwordHash == null) {
            passwordHash = passwordEncoder.encode(PASSWORD);   // bcrypt chậm: băm một lần, dùng lại
        }
        return passwordHash;
    }

    public record OrganizerRef(UUID organizerId, UUID userId, String email) {}

    /** {@code OffsetDateTime} UTC: kiểu mà driver PostgreSQL map chắc chắn vào {@code timestamptz}. */
    private OffsetDateTime now() {
        return OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
    }
}
