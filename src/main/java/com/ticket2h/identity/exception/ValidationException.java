package com.ticket2h.identity.exception;

public class ValidationException extends BusinessException {

    public ValidationException(String detail) {
        super(ErrorCode.VALIDATION_FAILED, detail);
    }
}
