package com.ticket2h.identity.controller;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.HttpCookie;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.ticket2h.identity.entity.Role;
import com.ticket2h.identity.support.Api;
import com.ticket2h.identity.support.IntegrationTest;
import com.ticket2h.identity.support.TestData;

class RefreshTokenApiIT extends IntegrationTest {

    private static final String COOKIE = "ticket2h_refresh";

    @Test
    void login_sets_an_http_only_strict_refresh_cookie() {
        testData.userWithEmail("a@test.local", Role.CUSTOMER, null);

        Api.Response login = login("a@test.local");

        String setCookie = login.header("Set-Cookie");
        assertThat(setCookie).startsWith(COOKIE + "=").contains("HttpOnly", "Secure", "SameSite=Strict", "Path=/auth");
        assertThat(login.json().get("accessToken").asString()).isNotBlank();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM refresh_tokens", Integer.class)).isEqualTo(1);
    }

    @Test
    void refresh_rotates_the_token_and_returns_a_working_access_token() {
        testData.userWithEmail("a@test.local", Role.CUSTOMER, null);
        String first = refreshCookie(login("a@test.local"));

        Api.Response refreshed = refresh(first);

        assertThat(refreshed.status()).isEqualTo(200);
        assertThat(refreshed.json().get("user").get("email").asString()).isEqualTo("a@test.local");
        String second = refreshCookie(refreshed);
        assertThat(second).isNotEqualTo(first);
        assertThat(api.get("/me", refreshed.json().get("accessToken").asString()).status()).isEqualTo(200);
        // Token cũ đã bị thay: dùng lại thì 401
        assertThat(refresh(first).status()).isEqualTo(401);
    }

    @Test
    void reusing_a_rotated_token_revokes_the_whole_family() {
        testData.userWithEmail("a@test.local", Role.CUSTOMER, null);
        String stolen = refreshCookie(login("a@test.local"));
        String current = refreshCookie(refresh(stolen));

        Api.Response reuse = refresh(stolen);

        assertThat(reuse.status()).isEqualTo(401);
        assertThat(reuse.code()).isEqualTo("UNAUTHENTICATED");
        assertThat(refresh(current).status()).isEqualTo(401);
    }

    @Test
    void reuse_in_one_family_does_not_touch_another_login() {
        testData.userWithEmail("a@test.local", Role.CUSTOMER, null);
        String phone = refreshCookie(login("a@test.local"));
        String laptop = refreshCookie(login("a@test.local"));
        refresh(phone);
        refresh(phone);   // dùng lại: family của phone bị thu hồi

        assertThat(refresh(laptop).status()).isEqualTo(200);
    }

    @Test
    void expired_refresh_token_is_rejected_and_the_cookie_cleared() {
        testData.userWithEmail("a@test.local", Role.CUSTOMER, null);
        String token = refreshCookie(login("a@test.local"));
        clock.advance(Duration.ofDays(15));

        Api.Response response = refresh(token);

        assertThat(response.status()).isEqualTo(401);
        assertThat(response.header("Content-Type")).startsWith("application/problem+json");
        assertThat(response.header("Set-Cookie")).startsWith(COOKIE + "=;").contains("Max-Age=0");
    }

    @Test
    void refresh_without_cookie_is_401() {
        Api.Response response = api.post("/auth/refresh", null, Map.of());

        assertThat(response.status()).isEqualTo(401);
        assertThat(response.code()).isEqualTo("UNAUTHENTICATED");
    }

    @Test
    void logout_revokes_the_refresh_token_and_clears_the_cookie() {
        testData.userWithEmail("a@test.local", Role.CUSTOMER, null);
        String token = refreshCookie(login("a@test.local"));

        Api.Response logout = api.post("/auth/logout", null, Map.of(), Map.of("Cookie", COOKIE + "=" + token));

        assertThat(logout.status()).isEqualTo(204);
        assertThat(logout.header("Set-Cookie")).contains("Max-Age=0");
        assertThat(refresh(token).status()).isEqualTo(401);
    }

    @Test
    void openapi_document_is_public() {
        Api.Response docs = api.get("/v3/api-docs", null);

        assertThat(docs.status()).isEqualTo(200);
        assertThat(docs.json().get("paths").has("/auth/refresh")).isTrue();
        // AuthUser điền từ JWT, không phải tham số query
        assertThat(docs.body()).doesNotContain("\"caller\"");
    }

    private Api.Response login(String email) {
        Api.Response response = api.post("/auth/login", null, Map.of("email", email, "password", TestData.PASSWORD));
        assertThat(response.status()).isEqualTo(200);
        return response;
    }

    private Api.Response refresh(String token) {
        return api.post("/auth/refresh", null, Map.of(), Map.of("Cookie", COOKIE + "=" + token));
    }

    private static String refreshCookie(Api.Response response) {
        List<HttpCookie> cookies = HttpCookie.parse(response.header("Set-Cookie"));
        return cookies.getFirst().getValue();
    }
}
