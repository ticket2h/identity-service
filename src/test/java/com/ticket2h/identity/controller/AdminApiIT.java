package com.ticket2h.identity.controller;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.ticket2h.identity.entity.Role;
import com.ticket2h.identity.support.Api;
import com.ticket2h.identity.support.IntegrationTest;
import com.ticket2h.identity.support.TestData;

/** ADMIN tạo ban tổ chức và tài khoản. */
class AdminApiIT extends IntegrationTest {

    private String admin;

    @BeforeEach
    void admin() {
        admin = testData.token(testData.user(Role.ADMIN));
    }

    @Test
    void admin_creates_an_organizer_then_an_organizer_account_in_it() {
        Api.Response organizer = api.post("/admin/organizers", admin, Map.of("name", "  Sky Music  "));
        assertThat(organizer.status()).isEqualTo(201);
        assertThat(organizer.json().get("name").asString()).isEqualTo("Sky Music");
        String organizerId = organizer.json().get("id").asString();

        Api.Response user = api.post("/admin/users", admin, account("org@test.local", "ORGANIZER", organizerId));

        assertThat(user.status()).isEqualTo(201);
        assertThat(user.json().get("organizerId").asString()).isEqualTo(organizerId);
    }

    @Test
    void organizer_account_needs_an_existing_organizer() {
        Api.Response missing = api.post("/admin/users", admin, account("a@test.local", "ORGANIZER", null));
        Api.Response unknown = api.post("/admin/users", admin,
                account("b@test.local", "ORGANIZER", UUID.randomUUID().toString()));

        assertThat(missing.status()).isEqualTo(400);
        assertThat(missing.code()).isEqualTo("VALIDATION_FAILED");
        assertThat(unknown.status()).isEqualTo(400);
    }

    @Test
    void only_organizer_accounts_belong_to_an_organizer() {
        String organizerId = testData.organizer().organizerId().toString();

        Api.Response staff = api.post("/admin/users", admin, account("s@test.local", "STAFF", organizerId));

        assertThat(staff.status()).isEqualTo(400);
    }

    @Test
    void non_admin_cannot_create_organizers() {
        String organizer = testData.token(testData.organizer().userId());

        Api.Response response = api.post("/admin/organizers", organizer, Map.of("name", "BTC lạ"));

        assertThat(response.status()).isEqualTo(403);
        assertThat(response.code()).isEqualTo("FORBIDDEN");
    }

    private static Map<String, Object> account(String email, String role, String organizerId) {
        Map<String, Object> body = new HashMap<>(Map.of("email", email, "password", TestData.PASSWORD,
                "fullName", "Tài khoản", "role", role));
        if (organizerId != null) {
            body.put("organizerId", organizerId);
        }
        return body;
    }
}
