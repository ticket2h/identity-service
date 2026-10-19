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
        assertThat(response.json().propertyNames()).containsExactlyInAnyOrder("items", "page", "size", "total");
        assertThat(response.json().get("page").asInt()).isZero();
        assertThat(response.json().get("size").asInt()).isEqualTo(20);
        assertThat(response.json().get("total").asLong()).isEqualTo(3);
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
        assertThat(emails(staff)).containsExactly("staff1@test.local", "staff2@test.local");
        assertThat(staff.json().get("total").asLong()).isEqualTo(2);
        assertThat(emails(admins)).containsExactly(ADMIN_EMAIL);
        assertThat(organizers.json().get("total").asLong()).isZero();
        assertThat(organizers.json().get("items").size()).isZero();
        assertThat(emptyRole.json().get("total").asLong()).isEqualTo(4);
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
        Api.Response an = api.get("/admin/users?q=an&role=CUSTOMER", admin);
        assertThat(emails(an)).containsExactly("nguyen.an@test.local");
        assertThat(an.json().get("total").asLong()).isEqualTo(1);
        // Kết hợp với role: Bình là STAFF
        assertThat(api.get("/admin/users?q=bình&role=CUSTOMER", admin).json().get("total").asLong()).isZero();
        // "_" là ký tự thường, không phải ký tự đại diện của LIKE
        assertThat(api.get("/admin/users?q=_", admin).json().get("total").asLong()).isZero();
        // q chỉ có khoảng trắng: không lọc
        assertThat(api.get("/admin/users?q=   ", admin).json().get("total").asLong()).isEqualTo(4);
    }

    @Test
    void pages_are_disjoint_and_report_the_total() {
        for (int i = 1; i <= 4; i++) {
            clock.advance(Duration.ofMinutes(1));
            testData.userWithEmail("u" + i + "@test.local", Role.CUSTOMER, null);
        }
        clock.advance(Duration.ofMinutes(1));
        // Tạo cùng lúc thì theo email
        testData.userWithEmail("same-b@test.local", Role.CUSTOMER, null);
        testData.userWithEmail("same-a@test.local", Role.CUSTOMER, null);

        Api.Response first = api.get("/admin/users?size=3", admin);
        Api.Response second = api.get("/admin/users?page=1&size=3", admin);
        Api.Response third = api.get("/admin/users?page=2&size=3", admin);
        Api.Response beyond = api.get("/admin/users?page=3&size=3", admin);

        assertThat(emails(first)).containsExactly("same-a@test.local", "same-b@test.local", "u4@test.local");
        assertThat(emails(second)).containsExactly("u3@test.local", "u2@test.local", "u1@test.local");
        assertThat(emails(third)).containsExactly(ADMIN_EMAIL);
        assertThat(emails(beyond)).isEmpty();
        for (Api.Response page : List.of(first, second, third, beyond)) {
            assertThat(page.json().get("total").asLong()).isEqualTo(7);
            assertThat(page.json().get("size").asInt()).isEqualTo(3);
        }
        assertThat(second.json().get("page").asInt()).isEqualTo(1);
        assertThat(beyond.json().get("page").asInt()).isEqualTo(3);
    }

    @Test
    void size_above_100_is_capped() {
        Api.Response response = api.get("/admin/users?size=1000", admin);

        assertThat(response.status()).isEqualTo(200);
        assertThat(response.json().get("size").asInt()).isEqualTo(100);
    }

    @Test
    void invalid_role_page_or_size_is_400() {
        for (String query : List.of("role=SUPERUSER", "role=staff", "page=-1", "page=10001", "page=abc", "size=0",
                "size=-5")) {
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
