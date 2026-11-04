package com.payvault.txn.repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import com.payvault.txn.entity.Transaction;
import com.payvault.txn.entity.TxnStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TransactionRepository extends JpaRepository<Transaction, Long> {

    Optional<Transaction> findByIdempotencyKey(String idempotencyKey);

    Optional<Transaction> findByTxnRef(String txnRef);

    @Query("select t from Transaction t where t.senderId = :userId or t.receiverId = :userId order by t.id desc")
    Page<Transaction> history(@Param("userId") Long userId, Pageable pageable);

    List<Transaction> findTop50ByStatusInAndUpdatedAtBeforeOrderByIdAsc(Collection<TxnStatus> statuses, Instant before);
}
