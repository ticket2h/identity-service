package com.ticket2h.identity.controller;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.ticket2h.identity.dto.request.CreateOrganizerRequest;
import com.ticket2h.identity.dto.request.CreateUserRequest;
import com.ticket2h.identity.dto.response.OrganizerResponse;
import com.ticket2h.identity.dto.response.UserResponse;
import com.ticket2h.identity.service.AccountService;

/** Chỉ ADMIN (chặn trong SecurityConfig theo đường dẫn /admin/**). */
@RestController
@RequestMapping("/admin")
class AdminController {

    private final AccountService accounts;

    AdminController(AccountService accounts) {
        this.accounts = accounts;
    }

    @PostMapping("/organizers")
    @ResponseStatus(HttpStatus.CREATED)
    OrganizerResponse createOrganizer(@Valid @RequestBody CreateOrganizerRequest request) {
        return OrganizerResponse.of(accounts.createOrganizer(request.name()));
    }

    @PostMapping("/users")
    @ResponseStatus(HttpStatus.CREATED)
    UserResponse createUser(@Valid @RequestBody CreateUserRequest request) {
        return UserResponse.of(accounts.createUser(request.email(), request.password(), request.fullName(),
                request.role(), request.organizerId()));
    }
}
