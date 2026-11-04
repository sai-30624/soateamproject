package com.payvault.wallet.service;

import java.math.BigDecimal;
import java.util.Optional;

import com.payvault.wallet.dto.WalletDtos.LedgerResult;
import com.payvault.wallet.dto.WalletDtos.ReconcileDto;
import com.payvault.wallet.entity.EntryType;
import com.payvault.wallet.entity.LedgerEntry;
import com.payvault.wallet.entity.Wallet;
import com.payvault.wallet.exception.ApiException;
import com.payvault.wallet.repository.LedgerEntryRepository;
import com.payvault.wallet.repository.WalletRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WalletService {

    private final WalletRepository wallets;
    private final LedgerEntryRepository ledger;

    public WalletService(WalletRepository wallets, LedgerEntryRepository ledger) {
        this.wallets = wallets;
        this.ledger = ledger;
    }

    /** Idempotent: creating a wallet that already exists returns the existing one. */
    public Wallet create(Long userId) {
        return wallets.findByUserId(userId).orElseGet(() -> {
            try {
                return wallets.saveAndFlush(new Wallet(userId));
            } catch (DataIntegrityViolationException raced) {
                return wallets.findByUserId(userId).orElseThrow();
            }
        });
    }

    public Wallet getOrCreate(Long userId) {
        return create(userId);
    }

    public Wallet get(Long userId) {
        return wallets.findByUserId(userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Wallet not found for user " + userId));
    }

    @Transactional
    public LedgerResult debit(Long userId, String txnRef, BigDecimal amount) {
        return apply(userId, txnRef, amount, EntryType.DEBIT);
    }

    @Transactional
    public LedgerResult credit(Long userId, String txnRef, BigDecimal amount) {
        return apply(userId, txnRef, amount, EntryType.CREDIT);
    }

    /**
     * The heart of PayVault: balance update + ledger row in ONE local database transaction.
     * Either both are saved or neither is.
     */
    @Transactional
    public LedgerResult apply(Long userId, String txnRef, BigDecimal amount, EntryType type) {
        Wallet wallet = wallets.findByUserIdForUpdate(userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Wallet not found for user " + userId));

        // Idempotency: this exact debit/credit was already applied -> return it, change nothing.
        Optional<LedgerEntry> existing = ledger.findByWalletIdAndTxnRefAndEntryType(wallet.getId(), txnRef, type);
        if (existing.isPresent()) {
            return LedgerResult.from(existing.get(), true);
        }

        BigDecimal before = wallet.getBalance();
        if (type == EntryType.DEBIT && before.compareTo(amount) < 0) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "Insufficient balance");
        }
        BigDecimal after = type == EntryType.DEBIT ? before.subtract(amount) : before.add(amount);
        wallet.setBalance(after);

        LedgerEntry entry = ledger.save(new LedgerEntry(wallet.getId(), txnRef, type, amount, before, after));
        return LedgerResult.from(entry, false);
    }

    public Page<LedgerEntry> statement(Long userId, int page, int size) {
        Wallet w = getOrCreate(userId);
        return ledger.findByWalletIdOrderByIdDesc(w.getId(), PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100)));
    }

    /** Audit check: the balance must equal (all credits - all debits) in the ledger. */
    public ReconcileDto reconcile(Long userId) {
        Wallet w = getOrCreate(userId);
        BigDecimal credits = ledger.sumByType(w.getId(), EntryType.CREDIT);
        BigDecimal debits = ledger.sumByType(w.getId(), EntryType.DEBIT);
        BigDecimal net = (credits == null ? BigDecimal.ZERO : credits).subtract(debits == null ? BigDecimal.ZERO : debits);
        return new ReconcileDto(userId, w.getBalance(), net, ledger.countByWalletId(w.getId()),
                w.getBalance().compareTo(net) == 0);
    }
}
