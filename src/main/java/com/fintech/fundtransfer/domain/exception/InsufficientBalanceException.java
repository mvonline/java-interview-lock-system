package com.fintech.fundtransfer.domain.exception;

import org.springframework.http.HttpStatus;

public class InsufficientBalanceException extends BaseException {
    public InsufficientBalanceException(Long walletId) {
        super("Insufficient balance or concurrency failure in wallet: " + walletId, HttpStatus.UNPROCESSABLE_ENTITY, ErrorCode.INSUFFICIENT_BALANCE);
    }
}
