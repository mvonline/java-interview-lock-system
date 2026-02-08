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

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class DeadlockPreventionIntegrationTest {

    @Autowired
    private TransferService transferService;

    @Autowired
    private WalletJpaRepository walletRepository;

    private Long walletAId;
    private Long walletBId;

    @BeforeEach
    void setup() {
        walletRepository.deleteAll();
        Wallet a = walletRepository.saveAndFlush(Wallet.builder().userId("A").balance(new BigDecimal("1000.00")).build());
        Wallet b = walletRepository.saveAndFlush(Wallet.builder().userId("B").balance(new BigDecimal("1000.00")).build());
        walletAId = a.getId();
        walletBId = b.getId();
    }

    @Test
    void testMutualTransfers_DeadlockPrevention() {
        int iterations = 20;
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CompletableFuture<?>[] futures = new CompletableFuture[iterations * 2];

        for (int i = 0; i < iterations; i++) {
            futures[i * 2] = CompletableFuture.runAsync(() -> {
                transferService.transferFunds(TransferRequest.builder()
                        .sourceWalletId(walletAId)
                        .destinationWalletId(walletBId)
                        .amount(new BigDecimal("1.00"))
                        .referenceCode(UUID.randomUUID().toString())
                        .build());
            }, executor);

            futures[i * 2 + 1] = CompletableFuture.runAsync(() -> {
                transferService.transferFunds(TransferRequest.builder()
                        .sourceWalletId(walletBId)
                        .destinationWalletId(walletAId)
                        .amount(new BigDecimal("1.00"))
                        .referenceCode(UUID.randomUUID().toString())
                        .build());
            }, executor);
        }

        CompletableFuture.allOf(futures).join();

        Wallet a = walletRepository.findById(walletAId).orElseThrow();
        Wallet b = walletRepository.findById(walletBId).orElseThrow();

        assertThat(a.getBalance()).isEqualByComparingTo(new BigDecimal("1000.00"));
        assertThat(b.getBalance()).isEqualByComparingTo(new BigDecimal("1000.00"));
    }
}
