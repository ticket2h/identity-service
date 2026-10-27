package com.ticket2h.identity.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.HttpCookie;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;

import com.ticket2h.identity.entity.Role;
import com.ticket2h.identity.support.Api;
import com.ticket2h.identity.support.IntegrationTest;
import com.ticket2h.identity.support.TestData;

import tools.jackson.databind.JsonNode;

/** Lịch sử thao tác của identity (ADR-023 ở infra/docs/adr). */
class AuditLogApiIT extends IntegrationTest {

    @Test
    void admin_actions_are_recorded_with_who_did_it() {
        UUID adminId = testData.userWithEmail("admin@test.local", Role.ADMIN, null);
        String admin = testData.token(adminId);
        String organizerId = api.post("/admin/organizers", admin, Map.of("name", "Sky Music")).json().get("id")
                .asString();
        clock.advance(Duration.ofSeconds(1));
        String userId = api.post("/admin/users", admin, account("org@test.local", "ORGANIZER", organizerId)).json()
                .get("id").asString();

        List<JsonNode> items = items(api.get("/admin/audit-logs", admin));

        assertThat(items).extracting(i -> i.get("action").asString())
                .containsExactly("USER_CREATED", "ORGANIZER_CREATED");
        JsonNode created = items.getFirst();
        assertThat(created.get("actor").get("type").asString()).isEqualTo("USER");
        assertThat(created.get("actor").get("id").asString()).isEqualTo(adminId.toString());
        assertThat(created.get("actor").get("role").asString()).isEqualTo("ADMIN");
        assertThat(created.get("actor").get("email").asString()).isEqualTo("admin@test.local");
        assertThat(created.get("targetType").asString()).isEqualTo("USER");
        assertThat(created.get("targetId").asString()).isEqualTo(userId);
        assertThat(created.get("targetEmail").asString()).isEqualTo("org@test.local");
        assertThat(created.get("details").get("role").asString()).isEqualTo("ORGANIZER");
        assertThat(created.get("details").get("organizerId").asString()).isEqualTo(organizerId);
        assertThat(items.get(1).get("details").get("name").asString()).isEqualTo("Sky Music");
        assertThat(created.toString()).as("không lưu mật khẩu hay hash").doesNotContain(TestData.PASSWORD, "bcrypt");
    }

    @Test
    void customers_signing_up_themselves_are_not_recorded() {
        assertThat(api.post("/auth/register", null, Map.of("email", "k@test.local", "password", TestData.PASSWORD,
                "fullName", "Khách")).status()).isEqualTo(201);

        assertThat(items(api.get("/admin/audit-logs", adminToken()))).isEmpty();
    }

    @Test
    void reused_refresh_token_is_recorded_as_a_security_event() {
        UUID userId = testData.userWithEmail("a@test.local", Role.CUSTOMER, null);
        String first = refreshCookie(api.post("/auth/login", null,
                Map.of("email", "a@test.local", "password", TestData.PASSWORD)));
        assertThat(refresh(first).status()).isEqualTo(200);

        assertThat(refresh(first).status()).as("token cũ bị gửi lại").isEqualTo(401);

        JsonNode event = items(api.get("/admin/audit-logs?action=REFRESH_TOKEN_REUSE_DETECTED", adminToken()))
                .getFirst();
        assertThat(event.get("actor").get("type").asString()).isEqualTo("SYSTEM");
        assertThat(event.get("actor").get("name").asString()).isEqualTo("refresh-token");
        assertThat(event.get("targetId").asString()).isEqualTo(userId.toString());
        assertThat(event.get("targetEmail").asString()).isEqualTo("a@test.local");
        assertThat(event.get("details").get("revokedTokens").asInt()).isEqualTo(1);
        assertThat(event.toString()).doesNotContain(first);
    }

    @Test
    void only_admins_read_the_history() {
        assertThat(api.get("/admin/audit-logs", testData.token(testData.organizer().userId())).status())
                .isEqualTo(403);
        assertThat(api.get("/admin/audit-logs", null).status()).isEqualTo(401);
    }

    @Test
    void history_pages_with_a_cursor() {
        String admin = adminToken();
        for (int i = 0; i < 5; i++) {
            api.post("/admin/organizers", admin, Map.of("name", "BTC " + i));
            clock.advance(Duration.ofSeconds(1));
        }

        Set<String> seen = new HashSet<>();
        List<String> names = new ArrayList<>();
        String cursor = null;
        do {
            JsonNode page = api.get("/admin/audit-logs?limit=2" + (cursor == null ? "" : "&cursor=" + cursor), admin)
                    .json();
            page.get("items").forEach(item -> {
                assertThat(seen.add(item.get("id").asString())).isTrue();
                names.add(item.get("details").get("name").asString());
            });
            cursor = page.get("nextCursor").isNull() ? null : page.get("nextCursor").asString();
        } while (cursor != null);

        assertThat(names).containsExactly("BTC 4", "BTC 3", "BTC 2", "BTC 1", "BTC 0");
        assertThat(api.get("/admin/audit-logs?cursor=khong-hop-le!", admin).status()).isEqualTo(400);
    }

    @Test
    void history_rows_cannot_be_changed_or_deleted() {
        api.post("/admin/organizers", adminToken(), Map.of("name", "Sky"));

        assertThatThrownBy(() -> jdbc.update("UPDATE audit_logs SET action = 'USER_CREATED'"))
                .isInstanceOf(DataAccessException.class).hasMessageContaining("chỉ được ghi thêm");
        assertThatThrownBy(() -> jdbc.update("DELETE FROM audit_logs"))
                .isInstanceOf(DataAccessException.class).hasMessageContaining("chỉ được ghi thêm");
    }

    private String adminToken() {
        return testData.token(testData.user(Role.ADMIN));
    }

    private Api.Response refresh(String token) {
        return api.post("/auth/refresh", null, Map.of(), Map.of("Cookie", "ticket2h_refresh=" + token));
    }

    private static String refreshCookie(Api.Response response) {
        return HttpCookie.parse(response.header("Set-Cookie")).getFirst().getValue();
    }

    private static List<JsonNode> items(Api.Response response) {
        assertThat(response.status()).isEqualTo(200);
        List<JsonNode> items = new ArrayList<>();
        response.json().get("items").forEach(items::add);
        return items;
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
