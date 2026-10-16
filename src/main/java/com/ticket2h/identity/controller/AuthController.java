package com.ticket2h.identity.controller;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.ticket2h.identity.dto.request.LoginRequest;
import com.ticket2h.identity.dto.request.RegisterRequest;
import com.ticket2h.identity.dto.response.LoginResponse;
import com.ticket2h.identity.dto.response.UserResponse;
import com.ticket2h.identity.security.AuthUser;
import com.ticket2h.identity.service.AccountService;

@RestController
class AuthController {

    private final AccountService accounts;

    AuthController(AccountService accounts) {
        this.accounts = accounts;
    }

    @PostMapping("/auth/register")
    @ResponseStatus(HttpStatus.CREATED)
    UserResponse register(@Valid @RequestBody RegisterRequest request) {
        return UserResponse.of(accounts.register(request.email(), request.password(), request.fullName()));
    }

    @PostMapping("/auth/login")
    LoginResponse login(@Valid @RequestBody LoginRequest request) {
        AccountService.LoginResult result = accounts.login(request.email(), request.password());
        return new LoginResponse(result.accessToken(), "Bearer", result.expiresInSeconds(),
                UserResponse.of(result.user()));
    }

    @GetMapping("/me")
    UserResponse me(AuthUser caller) {
        return UserResponse.of(accounts.get(caller.id()));
    }
}
