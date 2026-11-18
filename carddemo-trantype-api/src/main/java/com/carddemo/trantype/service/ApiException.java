package com.carddemo.trantype.service;

import org.springframework.http.HttpStatus;

public class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final ErrorCode code;
    private final Integer sqlcode;

    public ApiException(HttpStatus status, ErrorCode code, String message, Integer sqlcode) {
        super(message);
        this.status = status;
        this.code = code;
        this.sqlcode = sqlcode;
    }

    public static ApiException validation(String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_ERROR, message, null);
    }

    public static ApiException notFound(String message) {
        return new ApiException(HttpStatus.NOT_FOUND, ErrorCode.NOT_FOUND, message, 100);
    }

    public HttpStatus getStatus() {
        return status;
    }

    public ErrorCode getCode() {
        return code;
    }

    public Integer getSqlcode() {
        return sqlcode;
    }
}
