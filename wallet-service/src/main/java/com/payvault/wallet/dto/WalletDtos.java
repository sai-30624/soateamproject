package com.payvault.wallet.dto;

import java.math.BigDecimal;
import java.time.Instant;

import com.payvault.wallet.entity.EntryType;
import com.payvault.wallet.entity.LedgerEntry;
import com.payvault.wallet.entity.Wallet;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public final class WalletDtos {
    private WalletDtos() {
    }

    public record CreateWalletRequest(@NotNull Long userId) {
    }

    public record MoneyRequest(
            @NotBlank @Size(max = 60) String txnRef,
            @NotNull @Positive @Digits(integer = 17, fraction = 2) BigDecimal amount) {
    }

    public record WalletDto(Long userId, BigDecimal balance, Instant updatedAt) {
        public static WalletDto from(Wallet w) {
            return new WalletDto(w.getUserId(), w.getBalance(), w.getUpdatedAt());
        }
    }

    public record LedgerDto(Long id, String txnRef, EntryType entryType, BigDecimal amount,
                            BigDecimal balanceBefore, BigDecimal balanceAfter, Instant createdAt) {
        public static LedgerDto from(LedgerEntry e) {
            return new LedgerDto(e.getId(), e.getTxnRef(), e.getEntryType(), e.getAmount(),
                    e.getBalanceBefore(), e.getBalanceAfter(), e.getCreatedAt());
        }
    }

    /** Result of a debit/credit. alreadyApplied=true means it was a retry of an entry that already exists. */
    public record LedgerResult(String txnRef, EntryType entryType, BigDecimal amount,
                               BigDecimal balanceBefore, BigDecimal balanceAfter, boolean alreadyApplied) {
        public static LedgerResult from(LedgerEntry e, boolean alreadyApplied) {
            return new LedgerResult(e.getTxnRef(), e.getEntryType(), e.getAmount(),
                    e.getBalanceBefore(), e.getBalanceAfter(), alreadyApplied);
        }
    }

    public record ReconcileDto(Long userId, BigDecimal walletBalance, BigDecimal ledgerNet,
                               long entries, boolean consistent) {
    }
}
