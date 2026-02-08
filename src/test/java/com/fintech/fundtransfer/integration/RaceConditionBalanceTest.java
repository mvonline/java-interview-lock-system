package com.fintech.fundtransfer.integration;

import com.fintech.fundtransfer.application.dto.TransferRequest;
import com.fintech.fundtransfer.application.service.TransferService;
import com.fintech.fundtransfer.domain.model.Wallet;
import com.fintech.fundtransfer.infrastructure.persistence.TransactionLogJpaRepository;
import com.fintech.fundtransfer.infrastructure.persistence.WalletJpaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class RaceConditionBalanceTest {

    @Autowired
    private TransferService transferService;

    @Autowired
    private WalletJpaRepository walletRepository;

    @Autowired
    private TransactionLogJpaRepository transactionLogRepository;

    @BeforeEach
    void setup() {
        transactionLogRepository.deleteAll();
        walletRepository.deleteAll();
    }

    @Test
    void testOverdraftRaceCondition() {
        Wallet source = walletRepository.saveAndFlush(Wallet.builder().userId("race-1").balance(new BigDecimal("100.00")).build());
        Wallet dest = walletRepository.saveAndFlush(Wallet.builder().userId("race-2").balance(new BigDecimal("0.00")).build());

        TransferRequest req1 = TransferRequest.builder()
                .sourceWalletId(source.getId())
                .destinationWalletId(dest.getId())
                .amount(new BigDecimal("60.00"))
                .referenceCode("ref-race-1")
                .build();

        TransferRequest req2 = TransferRequest.builder()
                .sourceWalletId(source.getId())
                .destinationWalletId(dest.getId())
                .amount(new BigDecimal("60.00"))
                .referenceCode("ref-race-2")
                .build();

        CompletableFuture<Void> f1 = CompletableFuture.runAsync(() -> transferService.transferFunds(req1));
        CompletableFuture<Void> f2 = CompletableFuture.runAsync(() -> transferService.transferFunds(req2));

        try {
            CompletableFuture.allOf(f1, f2).join();
        } catch (Exception ignored) {
        }

        Wallet finalSource = walletRepository.findById(source.getId()).orElseThrow();
        assertThat(finalSource.getBalance()).isEqualByComparingTo(new BigDecimal("40.00"));
        assertThat(finalSource.getBalance()).isGreaterThanOrEqualTo(BigDecimal.ZERO);
    }
}
