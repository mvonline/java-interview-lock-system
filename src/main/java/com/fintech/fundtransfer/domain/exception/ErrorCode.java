package com.fintech.fundtransfer.domain.exception;

public enum ErrorCode {
    WALLET_NOT_FOUND("WALLET_NOT_FOUND"),
    INSUFFICIENT_BALANCE("INSUFFICIENT_BALANCE"),
    LOCK_ACQUISITION_FAILED("LOCK_ACQUISITION_FAILED"),
    DUPLICATE_TRANSACTION("DUPLICATE_TRANSACTION"),
    CONCURRENCY_FAILURE("CONCURRENCY_FAILURE"),
    INTERNAL_SERVER_ERROR("INTERNAL_SERVER_ERROR");

    private final String value;

    ErrorCode(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }
}
