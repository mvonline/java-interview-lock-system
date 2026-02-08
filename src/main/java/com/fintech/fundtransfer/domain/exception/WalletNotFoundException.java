package com.fintech.fundtransfer.domain.exception;

import org.springframework.http.HttpStatus;

public class WalletNotFoundException extends BaseException {
    public WalletNotFoundException(Long id) {
        super("Wallet with ID " + id + " not found", HttpStatus.NOT_FOUND, ErrorCode.WALLET_NOT_FOUND);
    }
}
