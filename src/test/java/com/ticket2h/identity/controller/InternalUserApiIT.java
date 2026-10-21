package com.ticket2h.identity.controller;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.ticket2h.identity.entity.Role;
import com.ticket2h.identity.support.Api;
import com.ticket2h.identity.support.IntegrationTest;
import com.ticket2h.identity.support.TestData;

import tools.jackson.databind.JsonNode;

/** API nội bộ cho core-service và notification-service (infra/docs/contracts.md). */
class InternalUserApiIT extends IntegrationTest {

    private static final Map<String, String> CORE = Map.of("X-Internal-Api-Key", CORE_KEY);
    private static final Map<String, String> NOTIFICATION = Map.of("X-Internal-Api-Key", NOTIFICATION_KEY);

    @Test
    void missing_or_wrong_key_is_401() {
        UUID user = testData.user();

        Api.Response missing = api.get("/internal/users/" + user, null);
        Api.Response wrong = api.get("/internal/users/" + user, null, Map.of("X-Internal-Api-Key", CORE_KEY + "x"));

        assertThat(missing.status()).isEqualTo(401);
        assertThat(wrong.status()).isEqualTo(401);
        assertThat(wrong.code()).isEqualTo("UNAUTHENTICATED");
        assertThat(wrong.json().get("detail").asString()).contains("X-Internal-Api-Key");
    }

    @Test
    void a_user_jwt_does_not_open_the_internal_api_even_for_admin() {
        UUID admin = testData.user(Role.ADMIN);

        assertThat(api.get("/internal/users/" + admin, testData.token(admin)).status()).isEqualTo(401);
        assertThat(api.post("/internal/users/lookup", testData.token(admin),
                Map.of("email", testData.emailOf(admin))).status()).isEqualTo(401);
    }

    @Test
    void both_client_keys_work_and_the_shape_is_exactly_the_contract() {
        UUID user = testData.user();

        for (Map<String, String> key : java.util.List.of(CORE, NOTIFICATION)) {
            Api.Response response = api.get("/internal/users/" + user, null, key);

            assertThat(response.status()).isEqualTo(200);
            assertThat(response.header("Cache-Control")).contains("no-store");
            assertThat(response.json().propertyNames())
                    .containsExactlyInAnyOrder("id", "email", "fullName", "role", "organizerId");
            assertThat(response.json().get("id").asString()).isEqualTo(user.toString());
            assertThat(response.json().get("role").asString()).isEqualTo("CUSTOMER");
            assertThat(response.json().hasNonNull("organizerId")).isFalse();
        }
    }

    @Test
    void organizer_account_carries_its_organizer_id() {
        TestData.OrganizerRef organizer = testData.organizer();

        Api.Response response = api.get("/internal/users/" + organizer.userId(), null, NOTIFICATION);

        assertThat(response.json().get("organizerId").asString()).isEqualTo(organizer.organizerId().toString());
    }

    @Test
    void unknown_or_malformed_id_is_404() {
        Api.Response unknown = api.get("/internal/users/" + UUID.randomUUID(), null, CORE);
        Api.Response malformed = api.get("/internal/users/not-a-uuid", null, CORE);

        assertThat(unknown.status()).isEqualTo(404);
        assertThat(unknown.code()).isEqualTo("NOT_FOUND");
        assertThat(malformed.status()).isEqualTo(404);
    }

    @Test
    void lookup_by_email_normalizes_case_and_spaces() {
        UUID staff = testData.userWithEmail("staff1@test.local", Role.STAFF, null);

        Api.Response response = api.post("/internal/users/lookup", null, Map.of("email", "  Staff1@TEST.local "), CORE);

        assertThat(response.status()).isEqualTo(200);
        assertThat(response.header("Cache-Control")).contains("no-store");
        assertThat(response.json().get("id").asString()).isEqualTo(staff.toString());
        assertThat(response.json().get("email").asString()).isEqualTo("staff1@test.local");
        assertThat(response.json().get("role").asString()).isEqualTo("STAFF");
    }

    @Test
    void lookup_unknown_email_is_404_and_blank_or_malformed_email_is_400() {
        Api.Response unknown = api.post("/internal/users/lookup", null, Map.of("email", "nobody@test.local"), CORE);
        Api.Response blank = api.post("/internal/users/lookup", null, Map.of("email", "  "), CORE);
        Api.Response malformed = api.post("/internal/users/lookup", null, Map.of("email", "not-an-email"), CORE);

        assertThat(unknown.status()).isEqualTo(404);
        assertThat(unknown.code()).isEqualTo("NOT_FOUND");
        assertThat(blank.status()).isEqualTo(400);
        assertThat(blank.code()).isEqualTo("VALIDATION_FAILED");
        assertThat(malformed.status()).isEqualTo(400);
    }

    @Test
    void batch_returns_the_accounts_that_exist_and_skips_unknown_ids() {
        UUID customer = testData.user();
        TestData.OrganizerRef organizer = testData.organizer();
        UUID unknown = UUID.randomUUID();

        Api.Response response = api.post("/internal/users/batch", null,
                Map.of("ids", List.of(customer, organizer.userId(), unknown, customer)), CORE);

        assertThat(response.status()).isEqualTo(200);
        assertThat(response.header("Cache-Control")).contains("no-store");
        JsonNode users = response.json().get("users");
        assertThat(users).hasSize(2);
        Map<String, JsonNode> byId = new java.util.HashMap<>();
        users.forEach(u -> byId.put(u.get("id").asString(), u));
        assertThat(byId.keySet()).containsExactlyInAnyOrder(customer.toString(), organizer.userId().toString());
        assertThat(byId.get(customer.toString()).propertyNames())
                .containsExactlyInAnyOrder("id", "email", "fullName", "role", "organizerId");
        assertThat(byId.get(organizer.userId().toString()).get("organizerId").asString())
                .isEqualTo(organizer.organizerId().toString());
    }

    @Test
    void batch_accepts_up_to_1000_ids_and_rejects_empty_or_larger_requests() {
        List<UUID> thousand = new java.util.ArrayList<>();
        for (int i = 0; i < 1_000; i++) {
            thousand.add(UUID.randomUUID());
        }
        thousand.set(500, testData.user());
        List<UUID> tooMany = new java.util.ArrayList<>(thousand);
        tooMany.add(UUID.randomUUID());

        Api.Response ok = api.post("/internal/users/batch", null, Map.of("ids", thousand), CORE);
        Api.Response empty = api.post("/internal/users/batch", null, Map.of("ids", List.of()), CORE);
        Api.Response large = api.post("/internal/users/batch", null, Map.of("ids", tooMany), CORE);
        Api.Response noKey = api.post("/internal/users/batch", null, Map.of("ids", List.of(thousand.get(500))));

        assertThat(ok.status()).isEqualTo(200);
        assertThat(ok.json().get("users")).hasSize(1);
        assertThat(empty.status()).isEqualTo(400);
        assertThat(large.status()).isEqualTo(400);
        assertThat(large.code()).isEqualTo("VALIDATION_FAILED");
        assertThat(noKey.status()).isEqualTo(401);
    }
}
