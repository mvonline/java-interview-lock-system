package com.fintech.fundtransfer.integration;

import com.fintech.fundtransfer.application.dto.TransferRequest;
import com.fintech.fundtransfer.application.service.TransferService;
import com.fintech.fundtransfer.domain.model.Wallet;
import com.fintech.fundtransfer.infrastructure.persistence.WalletJpaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class TransferServiceConcurrencyIntegrationTest {

    @Autowired
    private TransferService transferService;

    @Autowired
    private WalletJpaRepository walletRepository;

    private Long sourceWalletId;
    private Long destWalletId;

    @BeforeEach
    void setup() {
        walletRepository.deleteAll();
        Wallet source = walletRepository.saveAndFlush(Wallet.builder()
                .userId("user-1")
                .balance(new BigDecimal("1000.00"))
                .build());
        Wallet dest = walletRepository.saveAndFlush(Wallet.builder()
                .userId("user-2")
                .balance(new BigDecimal("0.00"))
                .build());
        sourceWalletId = source.getId();
        destWalletId = dest.getId();
    }

    @Test
    void testConcurrentTransfers() {
        int concurrencyCount = 50;
        BigDecimal transferAmount = new BigDecimal("10.00");
        ExecutorService executor = Executors.newFixedThreadPool(concurrencyCount);
        
        CompletableFuture<?>[] futures = new CompletableFuture[concurrencyCount];
        AtomicInteger successCount = new AtomicInteger();
        AtomicInteger failureCount = new AtomicInteger();

        for (int i = 0; i < concurrencyCount; i++) {
            futures[i] = CompletableFuture.runAsync(() -> {
                try {
                    transferService.transferFunds(TransferRequest.builder()
                            .sourceWalletId(sourceWalletId)
                            .destinationWalletId(destWalletId)
                            .amount(transferAmount)
                            .referenceCode(UUID.randomUUID().toString())
                            .build());
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    failureCount.incrementAndGet();
                }
            }, executor);
        }

        CompletableFuture.allOf(futures).join();

        Wallet source = walletRepository.findById(sourceWalletId).orElseThrow();
        Wallet dest = walletRepository.findById(destWalletId).orElseThrow();

        // Assert consistency
        BigDecimal expectedSourceBalance = new BigDecimal("1000.00")
                .subtract(transferAmount.multiply(new BigDecimal(successCount.get())));
        BigDecimal expectedDestBalance = transferAmount.multiply(new BigDecimal(successCount.get()));

        assertThat(source.getBalance()).isEqualByComparingTo(expectedSourceBalance);
        assertThat(dest.getBalance()).isEqualByComparingTo(expectedDestBalance);
        assertThat(source.getBalance()).isGreaterThanOrEqualTo(BigDecimal.ZERO);
    }
}
