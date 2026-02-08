package com.fintech.fundtransfer.domain.exception;

import org.springframework.http.HttpStatus;

public class LockAcquisitionException extends BaseException {
    public LockAcquisitionException(String reason) {
        super("Failed to acquire distributed lock: " + reason, HttpStatus.SERVICE_UNAVAILABLE, ErrorCode.LOCK_ACQUISITION_FAILED);
    }
}
