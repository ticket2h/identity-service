package com.ticket2h.identity.dto.response;

import java.util.List;

/** Các tài khoản tìm thấy, không theo thứ tự nào. id không có tài khoản (đã xoá, sai) thì không có trong danh sách. */
public record UserBatchResponse(List<UserResponse> users) {}
