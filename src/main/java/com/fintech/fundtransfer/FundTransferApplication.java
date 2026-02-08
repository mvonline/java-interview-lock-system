package com.fintech.fundtransfer;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.retry.annotation.EnableRetry;

@SpringBootApplication
@EnableRetry
public class FundTransferApplication {
    public static void main(String[] args) {
        SpringApplication.run(FundTransferApplication.class, args);
    }
}
