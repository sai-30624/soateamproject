package com.payvault.wallet.repository;

import java.math.BigDecimal;
import java.util.Optional;

import com.payvault.wallet.entity.EntryType;
import com.payvault.wallet.entity.LedgerEntry;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LedgerEntryRepository extends JpaRepository<LedgerEntry, Long> {

    Optional<LedgerEntry> findByWalletIdAndTxnRefAndEntryType(Long walletId, String txnRef, EntryType entryType);

    Page<LedgerEntry> findByWalletIdOrderByIdDesc(Long walletId, Pageable pageable);

    @Query("select sum(e.amount) from LedgerEntry e where e.walletId = :walletId and e.entryType = :type")
    BigDecimal sumByType(@Param("walletId") Long walletId, @Param("type") EntryType type);

    long countByWalletId(Long walletId);
}
