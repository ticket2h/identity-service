package com.ticket2h.identity.controller;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.ticket2h.identity.dto.request.CreateOrganizerRequest;
import com.ticket2h.identity.dto.request.CreateUserRequest;
import com.ticket2h.identity.dto.response.OrganizerResponse;
import com.ticket2h.identity.dto.response.UserPageResponse;
import com.ticket2h.identity.dto.response.UserResponse;
import com.ticket2h.identity.entity.Role;
import com.ticket2h.identity.entity.User;
import com.ticket2h.identity.service.AccountService;

/** Chỉ ADMIN (chặn trong SecurityConfig theo đường dẫn /admin/**). */
@RestController
@RequestMapping("/admin")
class AdminController {

    /** {@code size} lớn hơn thì dùng giá trị này (như {@code GET /events} của core-service). */
    static final int MAX_PAGE_SIZE = 100;
    /** Giới hạn trên của {@code page}: page * size không vượt int (Spring Data cần OFFSET kiểu int). */
    static final int MAX_PAGE = 10_000;

    private final AccountService accounts;

    AdminController(AccountService accounts) {
        this.accounts = accounts;
    }

    @PostMapping("/organizers")
    @ResponseStatus(HttpStatus.CREATED)
    OrganizerResponse createOrganizer(@Valid @RequestBody CreateOrganizerRequest request) {
        return OrganizerResponse.of(accounts.createOrganizer(request.name()));
    }

    /** Mọi ban tổ chức, theo tên không phân biệt hoa thường (ô chọn ban tổ chức khi tạo tài khoản ORGANIZER). */
    @GetMapping("/organizers")
    List<OrganizerResponse> organizers() {
        return accounts.listOrganizers().stream().map(OrganizerResponse::of).toList();
    }

    @PostMapping("/users")
    @ResponseStatus(HttpStatus.CREATED)
    UserResponse createUser(@Valid @RequestBody CreateUserRequest request) {
        return UserResponse.of(accounts.createUser(request.email(), request.password(), request.fullName(),
                request.role(), request.organizerId()));
    }

    /**
     * Danh sách tài khoản, mới tạo trước. {@code role} lạ (không phải tên vai trò, phân biệt hoa thường) thì 400
     * (ApiExceptionHandler.handleTypeMismatch); {@code role=} rỗng coi như không lọc. {@code q}: chuỗi con của email
     * hoặc họ tên, không phân biệt hoa thường. {@code page} ngoài 0..10 000 hay {@code size} nhỏ hơn 1 thì 400;
     * {@code size} lớn hơn 100 thì dùng 100, {@code size} trong kết quả là số đã dùng.
     */
    @GetMapping("/users")
    UserPageResponse users(@RequestParam(required = false) Role role, @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") @Min(0) @Max(MAX_PAGE) int page,
            @RequestParam(defaultValue = "20") @Min(1) int size) {
        int safeSize = Math.min(size, MAX_PAGE_SIZE);
        Page<User> result = accounts.searchUsers(role, q, page, safeSize);
        return new UserPageResponse(result.getContent().stream().map(UserResponse::of).toList(), page, safeSize,
                result.getTotalElements());
    }
}
