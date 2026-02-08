package com.fintech.fundtransfer.application.service;

import com.fintech.fundtransfer.application.dto.TransferRequest;
import com.fintech.fundtransfer.application.dto.TransferResponse;
import com.fintech.fundtransfer.domain.exception.BaseException;
import com.fintech.fundtransfer.domain.exception.InsufficientBalanceException;
import com.fintech.fundtransfer.domain.model.TransactionLog;
import com.fintech.fundtransfer.domain.model.Wallet;
import com.fintech.fundtransfer.infrastructure.persistence.TransactionLogJpaRepository;
import com.fintech.fundtransfer.infrastructure.persistence.WalletJpaRepository;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TransferServiceUnitTest {

    @Mock
    private WalletJpaRepository walletRepository;
    @Mock
    private TransactionLogJpaRepository transactionLogRepository;
    @Mock
    private RedissonClient redissonClient;
    
    private MeterRegistry meterRegistry;

    private TransferService transferService;

    @Mock
    private RLock lock1;
    @Mock
    private RLock lock2;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        transferService = new TransferService(walletRepository, transactionLogRepository, redissonClient, meterRegistry);
    }

    @Test
    void testTransferFunds_IdempotencySuccess() {
        TransferRequest request = new TransferRequest(1L, 2L, new BigDecimal("100.00"), "ref-1");
        TransactionLog tx = TransactionLog.builder().id(100L).referenceCode("ref-1").build();
        when(transactionLogRepository.findByReferenceCode("ref-1")).thenReturn(Optional.of(tx));
        when(walletRepository.findById(1L)).thenReturn(Optional.of(Wallet.builder().id(1L).balance(new BigDecimal("500.00")).build()));

        TransferResponse response = transferService.transferFunds(request);

        assertNotNull(response);
        assertEquals(100L, response.getTransactionId());
        verify(walletRepository, never()).decrementBalance(anyLong(), any());
    }

    @Test
    void testTransferFunds_InsufficientBalance_ThrowsException() throws InterruptedException {
        TransferRequest request = new TransferRequest(1L, 2L, new BigDecimal("1000.00"), "ref-2");
        
        when(transactionLogRepository.findByReferenceCode(anyString())).thenReturn(Optional.empty());
        when(redissonClient.getLock(anyString())).thenReturn(lock1).thenReturn(lock2);
        when(lock1.tryLock(anyLong(), anyLong(), any())).thenReturn(true);
        when(lock2.tryLock(anyLong(), anyLong(), any())).thenReturn(true);
        when(lock1.isHeldByCurrentThread()).thenReturn(true);
        when(lock2.isHeldByCurrentThread()).thenReturn(true);

        when(walletRepository.findById(1L)).thenReturn(Optional.of(Wallet.builder().id(1L).balance(new BigDecimal("100.00")).build()));
        when(walletRepository.findById(2L)).thenReturn(Optional.of(Wallet.builder().id(2L).balance(new BigDecimal("50.00")).build()));
        
        // Layer 3 check fails
        when(walletRepository.decrementBalance(anyLong(), any())).thenReturn(0);

        assertThrows(InsufficientBalanceException.class, () -> transferService.transferFunds(request));
        
        verify(lock1).unlock();
        verify(lock2).unlock();
    }
}
