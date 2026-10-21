package com.ticket2h.identity.dev;

import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import javax.sql.DataSource;

import org.postgresql.PGConnection;
import org.postgresql.copy.CopyIn;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import com.ticket2h.identity.entity.Role;

/**
 * Công cụ sinh tài khoản cho M4 (profile {@code datagen}, ADR-021 ở infra/docs/adr): ban tổ chức, tài khoản ORGANIZER,
 * nhân viên soát vé và 2 triệu khách, tạo dần đều trong 2 năm. Chạy xong thì tắt app. Id theo {@link DatagenIds} để
 * core-service sinh đơn cho đúng các tài khoản này.
 * <p>
 * Nạp bằng {@code COPY ... FROM STDIN} trong một transaction. Mọi tài khoản dùng chung một hash của mật khẩu
 * {@value DevAccounts#PASSWORD}: băm BCrypt 2 triệu lần mất hàng giờ, mà dữ liệu này chỉ để thử tải. Email dạng
 * {@code khach{i}@datagen.ticket2h.local}, đăng nhập được.
 */
@Component
@Profile("datagen")
@Order(2)   // sau tài khoản mẫu (profile dev), vì chạy xong thì tắt app
class DataGenerator implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DataGenerator.class);
    private static final Duration HISTORY = Duration.ofDays(730);

    private static final String[] FAMILY = {"Nguyễn", "Trần", "Lê", "Phạm", "Hoàng", "Huỳnh", "Phan", "Vũ", "Võ",
            "Đặng", "Bùi", "Đỗ", "Hồ", "Ngô", "Dương", "Lý"};
    private static final String[] MIDDLE = {"Văn", "Thị", "Hữu", "Minh", "Ngọc", "Thanh", "Đức", "Quang", "Thu",
            "Hoài", "Gia", "Bảo", "Anh", "Xuân"};
    private static final String[] GIVEN = {"An", "Bình", "Châu", "Dũng", "Giang", "Hà", "Hải", "Hạnh", "Hiếu", "Hoa",
            "Huy", "Hương", "Khánh", "Lan", "Linh", "Long", "Mai", "Minh", "Nam", "Ngân", "Nhung", "Phong", "Phúc",
            "Quân", "Quỳnh", "Sơn", "Tâm", "Thảo", "Trang", "Trung", "Tú", "Tuấn", "Uyên", "Việt", "Vy", "Yến"};
    private static final String[] BRAND = {"Sao Mai", "Bình Minh", "Hoàng Hôn", "Mặt Trời", "Phương Nam", "Sông Hồng",
            "Ánh Dương", "Biển Xanh", "Đất Việt", "Thăng Long"};

    private final DatagenProperties props;
    private final DataSource dataSource;
    private final JdbcTemplate jdbc;
    private final PasswordEncoder passwords;
    private final Clock clock;
    private final ConfigurableApplicationContext context;

    DataGenerator(DatagenProperties props, DataSource dataSource, JdbcTemplate jdbc, PasswordEncoder passwords,
            Clock clock, ConfigurableApplicationContext context) {
        this.props = props;
        this.dataSource = dataSource;
        this.jdbc = jdbc;
        this.passwords = passwords;
        this.clock = clock;
        this.context = context;
    }

    @Override
    public void run(ApplicationArguments args) {
        int exitCode = 0;
        try {
            generate();
        } catch (Exception e) {
            log.error("Sinh tài khoản thất bại", e);
            exitCode = 1;
        }
        int code = exitCode;
        System.exit(SpringApplication.exit(context, () -> code));
    }

    void generate() throws SQLException {
        Integer existing = jdbc.queryForObject("SELECT count(*) FROM users WHERE id = ?", Integer.class,
                DatagenIds.customer(1));
        if (existing != null && existing > 0) {
            log.warn("Database đã có tài khoản sinh, dừng. Muốn sinh lại thì xoá volume database "
                    + "(docker compose down -v).");
            return;
        }
        long started = System.nanoTime();
        Instant now = clock.instant().truncatedTo(ChronoUnit.SECONDS);
        Instant first = now.minus(HISTORY).minus(Duration.ofDays(60));
        String hash = passwords.encode(DevAccounts.PASSWORD);
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try (Statement s = c.createStatement()) {
                s.execute("SET LOCAL synchronous_commit = off");
            }
            Copy organizers = new Copy(c, "organizers", "id, name, created_at");
            for (int i = 1; i <= props.organizers(); i++) {
                organizers.row(DatagenIds.organizer(i), "Công ty Giải trí " + BRAND[i % BRAND.length] + " " + i, first);
            }
            organizers.close();
            Copy users = new Copy(c, "users", "id, email, password_hash, full_name, role, organizer_id, created_at");
            for (int i = 1; i <= props.organizers(); i++) {
                users.row(DatagenIds.organizerAccount(i), "btc" + i + "@datagen.ticket2h.local", hash,
                        "Ban tổ chức " + i, Role.ORGANIZER.name(), DatagenIds.organizer(i), first);
            }
            for (int i = 1; i <= props.staff(); i++) {
                users.row(DatagenIds.staff(i), "soatve" + i + "@datagen.ticket2h.local", hash, name(i + 7_919L),
                        Role.STAFF.name(), null, first);
            }
            long span = HISTORY.getSeconds();
            for (int i = 1; i <= props.customers(); i++) {
                // Khách đăng ký dần đều trong 2 năm, id càng lớn đăng ký càng muộn
                Instant created = now.minus(HISTORY).plusSeconds(span * (i - 1) / Math.max(1, props.customers()));
                users.row(DatagenIds.customer(i), "khach" + i + "@datagen.ticket2h.local", hash, name(i),
                        Role.CUSTOMER.name(), null, created);
                if (i % 500_000 == 0) {
                    log.info("Đã ghi {} khách, {} s", i, (System.nanoTime() - started) / 1_000_000_000);
                }
            }
            users.close();
            c.commit();
        }
        jdbc.execute("ANALYZE users");
        jdbc.execute("ANALYZE organizers");
        log.info("Xong: {} ban tổ chức, {} nhân viên, {} khách trong {} s (mật khẩu {})", props.organizers(),
                props.staff(), props.customers(), (System.nanoTime() - started) / 1_000_000_000, DevAccounts.PASSWORD);
    }

    /** Họ tên tiếng Việt tất định theo số thứ tự. */
    static String name(long i) {
        long h = i * 0x9E3779B97F4A7C15L;
        return FAMILY[(int) Long.remainderUnsigned(h, FAMILY.length)] + " "
                + MIDDLE[(int) Long.remainderUnsigned(h >>> 16, MIDDLE.length)] + " "
                + GIVEN[(int) Long.remainderUnsigned(h >>> 32, GIVEN.length)];
    }

    /**
     * {@code COPY ... FROM STDIN} dạng CSV: PostgreSQL nhận một luồng dòng và ghi thẳng vào bảng, nhanh hơn INSERT
     * nhiều lần. Đệm khoảng 64KB rồi mới gửi; không giữ cả bảng trong bộ nhớ.
     */
    private static final class Copy {

        private final CopyIn copy;
        private final StringBuilder buffer = new StringBuilder(70_000);

        Copy(Connection connection, String table, String columns) throws SQLException {
            this.copy = connection.unwrap(PGConnection.class).getCopyAPI()
                    .copyIn("COPY " + table + " (" + columns + ") FROM STDIN WITH (FORMAT csv)");
        }

        void row(Object... values) throws SQLException {
            for (int i = 0; i < values.length; i++) {
                if (i > 0) {
                    buffer.append(',');
                }
                if (values[i] != null) {   // ô trống không ngoặc kép là NULL
                    String text = values[i].toString();
                    boolean quote = text.indexOf(',') >= 0 || text.indexOf('"') >= 0 || text.isEmpty();
                    buffer.append(quote ? '"' + text.replace("\"", "\"\"") + '"' : text);
                }
            }
            buffer.append('\n');
            if (buffer.length() >= 64 * 1024) {
                flush();
            }
        }

        void close() throws SQLException {
            flush();
            copy.endCopy();
        }

        private void flush() throws SQLException {
            byte[] bytes = buffer.toString().getBytes(StandardCharsets.UTF_8);
            copy.writeToCopy(bytes, 0, bytes.length);
            buffer.setLength(0);
        }
    }
}
