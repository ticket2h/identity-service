package com.ticket2h.identity.controller;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.ticket2h.identity.entity.Role;
import com.ticket2h.identity.support.Api;
import com.ticket2h.identity.support.IntegrationTest;
import com.ticket2h.identity.support.TestData;

/** Chuyển từ core-service (AuthApiIT) khi tách identity-service. */
class AuthApiIT extends IntegrationTest {

    @Test
    void register_then_login_returns_jwt_with_customer_role() {
        Api.Response registered = api.post("/auth/register", null,
                Map.of("email", "Buyer@Test.Local", "password", "Passw0rd!", "fullName", "Người mua"));
        assertThat(registered.status()).isEqualTo(201);
        assertThat(registered.json().get("email").asString()).isEqualTo("buyer@test.local");
        assertThat(registered.json().get("role").asString()).isEqualTo("CUSTOMER");

        Api.Response login = api.post("/auth/login", null, Map.of("email", "BUYER@test.local", "password", "Passw0rd!"));
        assertThat(login.status()).isEqualTo(200);
        assertThat(login.json().get("tokenType").asString()).isEqualTo("Bearer");
        assertThat(login.json().get("expiresIn").asLong()).isEqualTo(3600);
        String token = login.json().get("accessToken").asString();

        Api.Response me = api.get("/me", token);
        assertThat(me.status()).isEqualTo(200);
        assertThat(me.json().get("email").asString()).isEqualTo("buyer@test.local");
        assertThat(me.json().has("passwordHash")).isFalse();
    }

    @Test
    void wrong_password_and_unknown_email_get_the_same_401() {
        testData.userWithEmail("a@test.local", Role.CUSTOMER, null);

        Api.Response wrongPassword = api.post("/auth/login", null, Map.of("email", "a@test.local", "password", "nope"));
        Api.Response unknown = api.post("/auth/login", null, Map.of("email", "x@test.local", "password", "nope"));

        assertThat(wrongPassword.status()).isEqualTo(401);
        assertThat(wrongPassword.code()).isEqualTo("INVALID_CREDENTIALS");
        assertThat(unknown.status()).isEqualTo(401);
        assertThat(unknown.json().get("detail")).isEqualTo(wrongPassword.json().get("detail"));
    }

    @Test
    void duplicate_email_is_rejected_with_409() {
        Map<String, String> body = Map.of("email", "dup@test.local", "password", "Passw0rd!", "fullName", "A");
        assertThat(api.post("/auth/register", null, body).status()).isEqualTo(201);

        Api.Response again = api.post("/auth/register", null, body);
        assertThat(again.status()).isEqualTo(409);
        assertThat(again.code()).isEqualTo("EMAIL_ALREADY_REGISTERED");
    }

    @Test
    void missing_or_expired_token_gets_401_problem_detail() {
        Api.Response missing = api.get("/me", null);
        assertThat(missing.status()).isEqualTo(401);
        assertThat(missing.code()).isEqualTo("UNAUTHENTICATED");
        assertThat(missing.header("Content-Type")).startsWith("application/problem+json");

        String token = testData.token(testData.user());
        clock.advance(Duration.ofHours(2));   // token sống 1 giờ; hạn kiểm tra theo cùng Clock
        assertThat(api.get("/me", token).status()).isEqualTo(401);
    }

    @Test
    void an_old_token_does_not_block_login() {
        String expired = testData.token(testData.userWithEmail("a@test.local", Role.CUSTOMER, null));
        clock.advance(Duration.ofHours(2));

        Api.Response login = api.post("/auth/login", expired, Map.of("email", "a@test.local", "password", TestData.PASSWORD));

        assertThat(login.status()).isEqualTo(200);
    }

    @Test
    void only_admin_can_create_staff_accounts() {
        String customer = testData.token(testData.user());
        String admin = testData.token(testData.user(Role.ADMIN));
        Map<String, Object> staff = Map.of("email", "staff@test.local", "password", TestData.PASSWORD,
                "fullName", "Soát vé", "role", "STAFF");

        Api.Response forbidden = api.post("/admin/users", customer, staff);
        assertThat(forbidden.status()).isEqualTo(403);
        assertThat(forbidden.code()).isEqualTo("FORBIDDEN");

        Api.Response created = api.post("/admin/users", admin, staff);
        assertThat(created.status()).isEqualTo(201);
        assertThat(created.json().get("role").asString()).isEqualTo("STAFF");
        assertThat(api.login("staff@test.local", TestData.PASSWORD)).isNotBlank();
    }
}
