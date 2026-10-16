package com.ticket2h.identity.exception;

public class ForbiddenException extends BusinessException {

    public ForbiddenException(String detail) {
        super(ErrorCode.FORBIDDEN, detail);
    }
}
