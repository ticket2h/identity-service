package com.ticket2h.identity.controller;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.ticket2h.identity.entity.Role;
import com.ticket2h.identity.support.Api;
import com.ticket2h.identity.support.IntegrationTest;
import com.ticket2h.identity.support.TestData;

import tools.jackson.databind.JsonNode;

/** ADMIN xem danh sách ban tổ chức và tài khoản (màn hình quản trị của web-app). */
class AdminListApiIT extends IntegrationTest {

    private static final String ADMIN_EMAIL = "admin@test.local";

    private String admin;

    @BeforeEach
    void admin() {
        admin = testData.token(testData.userWithEmail(ADMIN_EMAIL, Role.ADMIN, null));
    }

    @Test
    void admin_lists_organizers_sorted_by_name_ignoring_case() {
        for (String name : List.of("beta Events", "Gamma", "Alpha Live", "alpha")) {
            assertThat(api.post("/admin/organizers", admin, Map.of("name", name)).status()).isEqualTo(201);
        }

        Api.Response response = api.get("/admin/organizers", admin);

        assertThat(response.status()).isEqualTo(200);
        assertThat(response.json().isArray()).isTrue();
        assertThat(values(response.json(), "name")).containsExactly("alpha", "Alpha Live", "beta Events", "Gamma");
        assertThat(response.json().get(0).propertyNames()).containsExactlyInAnyOrder("id", "name");
    }

    @Test
    void no_organizer_yet_is_an_empty_array() {
        Api.Response response = api.get("/admin/organizers", admin);

        assertThat(response.status()).isEqualTo(200);
        assertThat(response.json().isArray()).isTrue();
        assertThat(response.json().size()).isZero();
    }

    @Test
    void users_come_newest_first_in_the_page_shape_without_password_hash() {
        clock.advance(Duration.ofMinutes(1));
        testData.userWithEmail("first@test.local", Role.CUSTOMER, null);
        clock.advance(Duration.ofMinutes(1));
        TestData.OrganizerRef organizer = testData.organizer();

        Api.Response response = api.get("/admin/users", admin);

        assertThat(response.status()).isEqualTo(200);
        assertThat(response.json().propertyNames()).containsExactlyInAnyOrder("items", "limit", "nextCursor");
        assertThat(response.json().get("limit").asInt()).isEqualTo(20);
        assertThat(response.json().get("nextCursor").isNull()).isTrue();
        assertThat(emails(response)).containsExactly(organizer.email(), "first@test.local", ADMIN_EMAIL);
        JsonNode newest = response.json().get("items").get(0);
        assertThat(newest.propertyNames())
                .containsExactlyInAnyOrder("id", "email", "fullName", "role", "organizerId");
        assertThat(newest.get("id").asString()).isEqualTo(organizer.userId().toString());
        assertThat(newest.get("role").asString()).isEqualTo("ORGANIZER");
        assertThat(newest.get("organizerId").asString()).isEqualTo(organizer.organizerId().toString());
    }

    @Test
    void filters_by_role() {
        testData.userWithEmail("staff1@test.local", Role.STAFF, null);
        testData.userWithEmail("staff2@test.local", Role.STAFF, null);
        testData.userWithEmail("buyer@test.local", Role.CUSTOMER, null);

        Api.Response staff = api.get("/admin/users?role=STAFF", admin);
        Api.Response admins = api.get("/admin/users?role=ADMIN", admin);
        Api.Response organizers = api.get("/admin/users?role=ORGANIZER", admin);
        Api.Response emptyRole = api.get("/admin/users?role=", admin);

        assertThat(staff.status()).isEqualTo(200);
        assertThat(emails(staff)).containsExactlyInAnyOrder("staff1@test.local", "staff2@test.local");
        assertThat(emails(admins)).containsExactly(ADMIN_EMAIL);
        assertThat(organizers.json().get("items").size()).isZero();
        assertThat(organizers.json().get("nextCursor").isNull()).isTrue();
        assertThat(emails(emptyRole)).hasSize(4);
    }

    @Test
    void q_matches_email_or_full_name_ignoring_case_and_surrounding_spaces() {
        testData.userNamed("nguyen.an@test.local", "Nguyễn Văn An", Role.CUSTOMER);
        testData.userNamed("binh@test.local", "Trần Thị Bình", Role.STAFF);
        testData.userNamed("cuong@test.local", "Lê Cường", Role.CUSTOMER);

        assertThat(emails(api.get("/admin/users?q=  NGUYEN.AN@TEST ", admin)))
                .containsExactly("nguyen.an@test.local");
        assertThat(emails(api.get("/admin/users?q=thị bình", admin))).containsExactly("binh@test.local");
        assertThat(emails(api.get("/admin/users?q=TRẦN", admin))).containsExactly("binh@test.local");
        assertThat(emails(api.get("/admin/users?q=lê cƯỜng", admin))).containsExactly("cuong@test.local");
        // "an" có trong email nguyen.an@ và trong tên "Nguyễn Văn An", chỉ tính một lần
        assertThat(emails(api.get("/admin/users?q=an&role=CUSTOMER", admin))).containsExactly("nguyen.an@test.local");
        // Kết hợp với role: Bình là STAFF
        assertThat(emails(api.get("/admin/users?q=bình&role=CUSTOMER", admin))).isEmpty();
        // "_" là ký tự thường, không phải ký tự đại diện của LIKE
        assertThat(emails(api.get("/admin/users?q=_", admin))).isEmpty();
        // q chỉ có khoảng trắng: không lọc
        assertThat(emails(api.get("/admin/users?q=   ", admin))).hasSize(4);
    }

    /** Đi hết danh sách theo nextCursor: không lặp, không sót, kể cả các tài khoản tạo cùng một thời điểm. */
    @Test
    void following_next_cursor_walks_every_user_once_newest_first() {
        for (int i = 1; i <= 4; i++) {
            clock.advance(Duration.ofMinutes(1));
            testData.userWithEmail("u" + i + "@test.local", Role.CUSTOMER, null);
        }
        clock.advance(Duration.ofMinutes(1));
        for (int i = 1; i <= 5; i++) {
            testData.userWithEmail("same" + i + "@test.local", Role.CUSTOMER, null);   // cùng created_at
        }

        List<String> walked = new ArrayList<>();
        String cursor = null;
        int pages = 0;
        do {
            Api.Response page = api.get("/admin/users?limit=3" + (cursor == null ? "" : "&cursor=" + cursor), admin);
            assertThat(page.status()).isEqualTo(200);
            assertThat(page.json().get("limit").asInt()).isEqualTo(3);
            walked.addAll(emails(page));
            cursor = page.json().get("nextCursor").isNull() ? null : page.json().get("nextCursor").asString();
            pages++;
        } while (cursor != null);

        assertThat(pages).isEqualTo(4);   // 10 tài khoản, 3 mỗi trang
        assertThat(walked).doesNotHaveDuplicates().hasSize(10);
        assertThat(walked.subList(0, 5)).allMatch(email -> email.startsWith("same"));
        assertThat(walked.subList(5, 10))
                .containsExactly("u4@test.local", "u3@test.local", "u2@test.local", "u1@test.local", ADMIN_EMAIL);
    }

    @Test
    void cursor_keeps_its_place_when_newer_users_are_created_meanwhile() {
        for (int i = 1; i <= 4; i++) {
            clock.advance(Duration.ofMinutes(1));
            testData.userWithEmail("u" + i + "@test.local", Role.CUSTOMER, null);
        }
        Api.Response first = api.get("/admin/users?limit=2", admin);
        clock.advance(Duration.ofMinutes(1));
        testData.userWithEmail("newcomer@test.local", Role.CUSTOMER, null);

        Api.Response second = api.get("/admin/users?limit=2&cursor=" + first.json().get("nextCursor").asString(),
                admin);

        // Với OFFSET, người mới đẩy u3 sang trang 2 và u3 bị hiện hai lần
        assertThat(emails(first)).containsExactly("u4@test.local", "u3@test.local");
        assertThat(emails(second)).containsExactly("u2@test.local", "u1@test.local");
    }

    @Test
    void limit_above_100_is_capped() {
        Api.Response response = api.get("/admin/users?limit=1000", admin);

        assertThat(response.status()).isEqualTo(200);
        assertThat(response.json().get("limit").asInt()).isEqualTo(100);
    }

    @Test
    void invalid_role_limit_or_cursor_is_400() {
        String notACursor = java.util.Base64.getUrlEncoder().encodeToString("hello".getBytes());
        for (String query : List.of("role=SUPERUSER", "role=staff", "limit=0", "limit=-5", "limit=abc",
                "cursor=%25%25%25", "cursor=" + notACursor)) {
            Api.Response response = api.get("/admin/users?" + query, admin);

            assertThat(response.status()).as(query).isEqualTo(400);
            assertThat(response.code()).as(query).isEqualTo("VALIDATION_FAILED");
        }
    }

    @Test
    void only_admin_can_list() {
        List<String> others = List.of(testData.token(testData.user(Role.CUSTOMER)),
                testData.token(testData.user(Role.STAFF)), testData.token(testData.organizer().userId()));

        for (String path : List.of("/admin/organizers", "/admin/users")) {
            for (String token : others) {
                Api.Response forbidden = api.get(path, token);
                assertThat(forbidden.status()).as(path).isEqualTo(403);
                assertThat(forbidden.code()).as(path).isEqualTo("FORBIDDEN");
            }
            Api.Response anonymous = api.get(path, null);
            assertThat(anonymous.status()).as(path).isEqualTo(401);
            assertThat(anonymous.code()).as(path).isEqualTo("UNAUTHENTICATED");
        }
    }

    private static List<String> emails(Api.Response response) {
        return values(response.json().get("items"), "email");
    }

    private static List<String> values(JsonNode array, String field) {
        List<String> values = new ArrayList<>();
        for (JsonNode item : array) {
            values.add(item.get(field).asString());
        }
        return values;
    }
}
