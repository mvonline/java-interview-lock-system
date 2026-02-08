package com.fintech.fundtransfer.infrastructure.persistence;

import com.fintech.fundtransfer.domain.model.TransactionLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface TransactionLogJpaRepository extends JpaRepository<TransactionLog, Long> {
    Optional<TransactionLog> findByReferenceCode(String referenceCode);
}
