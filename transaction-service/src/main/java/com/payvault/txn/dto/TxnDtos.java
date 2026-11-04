package com.payvault.txn.dto;

import java.math.BigDecimal;
import java.time.Instant;

import com.payvault.txn.entity.Transaction;
import com.payvault.txn.entity.TxnStatus;
import com.payvault.txn.entity.TxnType;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public final class TxnDtos {
    private TxnDtos() {
    }

    public record TransferRequest(
            @NotNull Long receiverId,
            @NotNull @Positive @Digits(integer = 9, fraction = 2) BigDecimal amount,
            @Size(max = 140) String note) {
    }

    public record DepositRequest(@NotNull @Positive @Digits(integer = 9, fraction = 2) BigDecimal amount) {
    }

    /** direction is relative to the viewer: SENT, RECEIVED or DEPOSIT. */
    public record TxnResponse(String txnRef, TxnType type, String direction, Long senderId, String senderName,
                              Long receiverId, String receiverName, BigDecimal amount, TxnStatus status,
                              String failureReason, String note, Instant createdAt, Instant updatedAt) {
        public static TxnResponse of(Transaction t, Long viewerId) {
            String direction = t.getType() == TxnType.DEPOSIT ? "DEPOSIT"
                    : viewerId != null && viewerId.equals(t.getSenderId()) ? "SENT" : "RECEIVED";
            return new TxnResponse(t.getTxnRef(), t.getType(), direction, t.getSenderId(), t.getSenderName(),
                    t.getReceiverId(), t.getReceiverName(), t.getAmount(), t.getStatus(), t.getFailureReason(),
                    t.getNote(), t.getCreatedAt(), t.getUpdatedAt());
        }
    }

    // ---- payloads exchanged with other services
    public record MoneyRequest(String txnRef, BigDecimal amount) {
    }

    public record UserDto(Long id, String fullName, String email, String phone, String status) {
    }
}
