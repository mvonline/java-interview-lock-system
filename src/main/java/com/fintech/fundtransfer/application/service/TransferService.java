package com.fintech.fundtransfer.application.service;

import com.fintech.fundtransfer.application.dto.TransferRequest;
import com.fintech.fundtransfer.application.dto.TransferResponse;
import com.fintech.fundtransfer.domain.exception.InsufficientBalanceException;
import com.fintech.fundtransfer.domain.exception.LockAcquisitionException;
import com.fintech.fundtransfer.domain.exception.WalletNotFoundException;
import com.fintech.fundtransfer.domain.model.TransactionLog;
import com.fintech.fundtransfer.domain.model.Wallet;
import com.fintech.fundtransfer.infrastructure.persistence.TransactionLogJpaRepository;
import com.fintech.fundtransfer.infrastructure.persistence.WalletJpaRepository;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.util.concurrent.TimeUnit;

@Service
@Slf4j
@RequiredArgsConstructor
public class TransferService {

    private final WalletJpaRepository walletRepository;
    private final TransactionLogJpaRepository transactionLogRepository;
    private final RedissonClient redissonClient;
    private final MeterRegistry meterRegistry;

    private static final String LOCK_PREFIX = "wallet_lock:";

    @Retryable(
            retryFor = {ObjectOptimisticLockingFailureException.class},
            maxAttempts = 3,
            backoff = @Backoff(delay = 100)
    )
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public TransferResponse transferFunds(TransferRequest request) {
        Timer.Sample totalTimerSample = Timer.start(meterRegistry);

        // 1. Idempotency Check
        var existingTx = transactionLogRepository.findByReferenceCode(request.getReferenceCode());
        if (existingTx.isPresent()) {
            log.info("Duplicate transaction detected for reference code: {}", request.getReferenceCode());
            TransactionLog tx = existingTx.get();
            Wallet source = walletRepository.findById(request.getSourceWalletId())
                    .orElseThrow(() -> new WalletNotFoundException(request.getSourceWalletId()));
            return TransferResponse.builder()
                    .transactionId(tx.getId())
                    .referenceCode(tx.getReferenceCode())
                    .sourceWalletBalanceAfter(source.getBalance())
                    .message("Duplicate transaction - returning existing state")
                    .build();
        }

        // 2. Deadlock Prevention (Sort IDs)
        Long firstId = Math.min(request.getSourceWalletId(), request.getDestinationWalletId());
        Long secondId = Math.max(request.getSourceWalletId(), request.getDestinationWalletId());

        RLock lock1 = redissonClient.getLock(LOCK_PREFIX + firstId);
        RLock lock2 = redissonClient.getLock(LOCK_PREFIX + secondId);

        Timer.Sample lockTimerSample = Timer.start(meterRegistry);
        try {
            // Layer 1: Distributed Locking
            boolean acquired1 = lock1.tryLock(5, 10, TimeUnit.SECONDS);
            boolean acquired2 = lock2.tryLock(5, 10, TimeUnit.SECONDS);

            if (acquired1 && acquired2) {
                lockTimerSample.stop(meterRegistry.timer("fund_transfer.lock_acquisition_time"));
                return executeTransfer(request);
            } else {
                throw new LockAcquisitionException("Unable to acquire locks for both wallets");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new LockAcquisitionException("Lock acquisition process was interrupted");
        } finally {
            if (lock2.isHeldByCurrentThread()) lock2.unlock();
            if (lock1.isHeldByCurrentThread()) lock1.unlock();
            totalTimerSample.stop(meterRegistry.timer("fund_transfer.transaction_duration"));
        }
    }

    private TransferResponse executeTransfer(TransferRequest request) {
        // Fetch entities for Layer 2: Optimistic Locking (@Version)
        Wallet source = walletRepository.findById(request.getSourceWalletId())
                .orElseThrow(() -> new WalletNotFoundException(request.getSourceWalletId()));
        walletRepository.findById(request.getDestinationWalletId())
                .orElseThrow(() -> new WalletNotFoundException(request.getDestinationWalletId()));

        // Layer 3: Database Integrity (Native Query)
        int updated = walletRepository.decrementBalance(source.getId(), request.getAmount());
        if (updated == 0) {
            throw new InsufficientBalanceException(source.getId());
        }

        walletRepository.incrementBalance(request.getDestinationWalletId(), request.getAmount());

        // Log the transaction
        TransactionLog logEntry = TransactionLog.builder()
                .sourceId(source.getId())
                .destinationId(request.getDestinationWalletId())
                .amount(request.getAmount())
                .status(TransactionLog.TransactionStatus.SUCCESS)
                .referenceCode(request.getReferenceCode())
                .build();
        TransactionLog savedTx = transactionLogRepository.save(logEntry);

        // Fetch updated balance for response
        Wallet updatedSource = walletRepository.findById(source.getId()).get();

        return TransferResponse.builder()
                .transactionId(savedTx.getId())
                .referenceCode(savedTx.getReferenceCode())
                .sourceWalletBalanceAfter(updatedSource.getBalance())
                .message("Transfer successful")
                .build();
    }
}
