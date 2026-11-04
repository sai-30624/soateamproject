package com.payvault.wallet.entity;

import java.math.BigDecimal;
import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/**
 * Append-only financial ledger. One row per balance change, never updated or deleted.
 * The unique key (wallet_id, txn_ref, entry_type) guarantees the same debit or credit
 * can never be applied twice, even if a request is retried.
 */
@Entity
@Table(name = "ledger_entries",
        uniqueConstraints = @UniqueConstraint(name = "uk_ledger_once", columnNames = {"wallet_id", "txn_ref", "entry_type"}),
        indexes = @Index(name = "idx_ledger_wallet", columnList = "wallet_id"))
public class LedgerEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "wallet_id", nullable = false, updatable = false)
    private Long walletId;

    @Column(name = "txn_ref", nullable = false, updatable = false, length = 60)
    private String txnRef;

    @Enumerated(EnumType.STRING)
    @Column(name = "entry_type", nullable = false, updatable = false, length = 10)
    private EntryType entryType;

    @Column(nullable = false, updatable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, updatable = false, precision = 19, scale = 2)
    private BigDecimal balanceBefore;

    @Column(nullable = false, updatable = false, precision = 19, scale = 2)
    private BigDecimal balanceAfter;

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    protected LedgerEntry() {
    }

    public LedgerEntry(Long walletId, String txnRef, EntryType entryType, BigDecimal amount,
                       BigDecimal balanceBefore, BigDecimal balanceAfter) {
        this.walletId = walletId;
        this.txnRef = txnRef;
        this.entryType = entryType;
        this.amount = amount;
        this.balanceBefore = balanceBefore;
        this.balanceAfter = balanceAfter;
    }

    public Long getId() { return id; }
    public Long getWalletId() { return walletId; }
    public String getTxnRef() { return txnRef; }
    public EntryType getEntryType() { return entryType; }
    public BigDecimal getAmount() { return amount; }
    public BigDecimal getBalanceBefore() { return balanceBefore; }
    public BigDecimal getBalanceAfter() { return balanceAfter; }
    public Instant getCreatedAt() { return createdAt; }
}
