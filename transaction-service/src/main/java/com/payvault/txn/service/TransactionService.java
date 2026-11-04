package com.payvault.txn.service;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;

import com.payvault.txn.client.UserGateway;
import com.payvault.txn.client.WalletGateway;
import com.payvault.txn.dto.TxnDtos.DepositRequest;
import com.payvault.txn.dto.TxnDtos.TransferRequest;
import com.payvault.txn.dto.TxnDtos.UserDto;
import com.payvault.txn.entity.Transaction;
import com.payvault.txn.entity.TxnStatus;
import com.payvault.txn.entity.TxnType;
import com.payvault.txn.exception.ApiException;
import com.payvault.txn.exception.WalletUnavailableException;
import com.payvault.txn.repository.TransactionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/**
 * Orchestrates money movement as a SAGA (the sender and receiver wallets live in another service,
 * so one SQL transaction cannot cover both):
 *
 *   PENDING --debit sender--> DEBITED --credit receiver--> SUCCESS
 *      |                         |
 *      | debit rejected          | credit rejected
 *      v                         v
 *    FAILED                  REVERSING --refund sender--> REVERSED
 *
 * Every step is saved before the next one, so after a crash RecoveryJob can resume from the saved status.
 */
@Service
public class TransactionService {

    private static final Logger log = LoggerFactory.getLogger(TransactionService.class);
    private static final DateTimeFormatter REF_TIME = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private static final SecureRandom RANDOM = new SecureRandom();

    private final TransactionRepository repo;
    private final WalletGateway wallet;
    private final UserGateway users;
    private final BigDecimal maxTransfer;
    private final BigDecimal maxDeposit;

    public TransactionService(TransactionRepository repo, WalletGateway wallet, UserGateway users,
                              @Value("${payvault.limits.max-transfer:50000}") BigDecimal maxTransfer,
                              @Value("${payvault.limits.max-deposit:100000}") BigDecimal maxDeposit) {
        this.repo = repo;
        this.wallet = wallet;
        this.users = users;
        this.maxTransfer = maxTransfer;
        this.maxDeposit = maxDeposit;
    }

    // ------------------------------------------------------------------ public operations

    public Transaction transfer(Long senderId, String idempotencyKey, TransferRequest req) {
        String key = scopedKey(senderId, idempotencyKey);
        var existing = repo.findByIdempotencyKey(key);
        if (existing.isPresent()) {
            log.info("Duplicate transfer request {} -> returning {}", key, existing.get().getTxnRef());
            return existing.get();
        }

        if (req.receiverId().equals(senderId)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "You cannot send money to yourself");
        }
        BigDecimal amount = checkAmount(req.amount(), maxTransfer);

        UserDto sender = users.get(senderId, "Sender not found");
        UserDto receiver = users.get(req.receiverId(), "Receiver not found");
        if (!"ACTIVE".equals(sender.status())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Your account is blocked");
        }
        if (!"ACTIVE".equals(receiver.status())) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "Receiver account is not active");
        }
        tryEnsureWallet(receiver.id());

        Transaction t = new Transaction();
        t.setTxnRef(newTxnRef());
        t.setIdempotencyKey(key);
        t.setType(TxnType.TRANSFER);
        t.setSenderId(sender.id());
        t.setSenderName(sender.fullName());
        t.setReceiverId(receiver.id());
        t.setReceiverName(receiver.fullName());
        t.setAmount(amount);
        t.setNote(req.note());
        t = saveNew(t, key);
        if (t.getStatus() != TxnStatus.PENDING) {
            return t;
        }
        log.info("Transfer {} started: {} -> {} amount {}", t.getTxnRef(), senderId, receiver.id(), amount);
        return runSaga(t);
    }

    public Transaction deposit(Long userId, String idempotencyKey, DepositRequest req) {
        String key = scopedKey(userId, idempotencyKey);
        var existing = repo.findByIdempotencyKey(key);
        if (existing.isPresent()) {
            return existing.get();
        }
        BigDecimal amount = checkAmount(req.amount(), maxDeposit);
        UserDto user = users.get(userId, "User not found");
        if (!"ACTIVE".equals(user.status())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Your account is blocked");
        }
        tryEnsureWallet(userId);

        Transaction t = new Transaction();
        t.setTxnRef(newTxnRef());
        t.setIdempotencyKey(key);
        t.setType(TxnType.DEPOSIT);
        t.setReceiverId(userId);
        t.setReceiverName(user.fullName());
        t.setAmount(amount);
        t.setNote("Wallet top-up");
        t = saveNew(t, key);
        if (t.getStatus() != TxnStatus.PENDING) {
            return t;
        }
        return runSaga(t);
    }

    public Page<Transaction> history(Long userId, int page, int size) {
        return repo.history(userId, PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100)));
    }

    public Transaction getForUser(String txnRef, Long userId) {
        Transaction t = repo.findByTxnRef(txnRef)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Transaction not found"));
        if (!userId.equals(t.getSenderId()) && !userId.equals(t.getReceiverId())) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Transaction not found");
        }
        return t;
    }

    // ------------------------------------------------------------------ the saga

    /** Moves a transaction forward from whatever state it is in. Safe to call again (idempotent wallet ops). */
    public Transaction runSaga(Transaction t) {
        if (t.getType() == TxnType.DEPOSIT) {
            if (t.getStatus() == TxnStatus.PENDING) {
                try {
                    wallet.credit(t.getReceiverId(), t.getTxnRef(), t.getAmount());
                    return move(t, TxnStatus.SUCCESS, null);
                } catch (ApiException e) {
                    return move(t, TxnStatus.FAILED, e.getMessage());
                } catch (WalletUnavailableException e) {
                    return move(t, TxnStatus.PENDING, e.getMessage() + " - will retry automatically");
                }
            }
            return t;
        }

        // Step 1: debit the sender
        if (t.getStatus() == TxnStatus.PENDING) {
            try {
                wallet.debit(t.getSenderId(), t.getTxnRef(), t.getAmount());
                t = move(t, TxnStatus.DEBITED, null);
            } catch (ApiException e) {
                return move(t, TxnStatus.FAILED, e.getMessage());
            } catch (WalletUnavailableException e) {
                return move(t, TxnStatus.PENDING, e.getMessage() + " - will retry automatically");
            }
        }

        // Step 2: credit the receiver
        if (t.getStatus() == TxnStatus.DEBITED) {
            try {
                wallet.credit(t.getReceiverId(), t.getTxnRef(), t.getAmount());
                log.info("Transfer {} SUCCESS", t.getTxnRef());
                return move(t, TxnStatus.SUCCESS, null);
            } catch (ApiException e) {
                t = move(t, TxnStatus.REVERSING, "Credit to receiver failed: " + e.getMessage());
            } catch (WalletUnavailableException e) {
                return move(t, TxnStatus.DEBITED, e.getMessage() + " - will retry automatically");
            }
        }

        // Compensation: refund the sender
        if (t.getStatus() == TxnStatus.REVERSING) {
            try {
                wallet.credit(t.getSenderId(), t.getTxnRef() + "-REV", t.getAmount());
                log.warn("Transfer {} REVERSED: {}", t.getTxnRef(), t.getFailureReason());
                return move(t, TxnStatus.REVERSED, t.getFailureReason() + " - amount refunded");
            } catch (RuntimeException e) {
                log.error("Refund for {} failed, recovery job will retry: {}", t.getTxnRef(), e.getMessage());
                return t;
            }
        }
        return t;
    }

    // ------------------------------------------------------------------ helpers

    /**
     * Makes sure the target wallet exists. If Wallet Service is down we still record the transaction as PENDING,
     * so the saga (or RecoveryJob, once Wallet is back) can finish it - nothing is lost.
     */
    private void tryEnsureWallet(Long userId) {
        try {
            wallet.ensureWallet(userId);
        } catch (WalletUnavailableException e) {
            log.warn("Could not verify wallet for user {} ({}); continuing, saga will retry", userId, e.getMessage());
        }
    }

    private Transaction move(Transaction t, TxnStatus status, String reason) {
        t.setStatus(status);
        // The column is 255 chars; error text from other services can be longer and must never break the saga.
        t.setFailureReason(reason != null && reason.length() > 255 ? reason.substring(0, 252) + "..." : reason);
        return repo.save(t);
    }

    private Transaction saveNew(Transaction t, String key) {
        try {
            return repo.saveAndFlush(t);
        } catch (DataIntegrityViolationException raced) {
            // Two identical requests arrived at the same moment - the other one won.
            return repo.findByIdempotencyKey(key).orElseThrow(() -> raced);
        }
    }

    private static String scopedKey(Long userId, String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank() || idempotencyKey.length() > 64) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Idempotency-Key header is required (max 64 chars)");
        }
        return userId + ":" + idempotencyKey.trim();
    }

    private static BigDecimal checkAmount(BigDecimal amount, BigDecimal max) {
        if (amount.signum() <= 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Amount must be positive");
        }
        if (amount.stripTrailingZeros().scale() > 2) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Amount can have at most 2 decimal places");
        }
        if (amount.compareTo(max) > 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Amount exceeds the limit of " + max.toPlainString());
        }
        return amount.setScale(2);
    }

    private static String newTxnRef() {
        byte[] b = new byte[3];
        RANDOM.nextBytes(b);
        return "TXN" + ZonedDateTime.now(ZoneOffset.UTC).format(REF_TIME) + "-" + HexFormat.of().withUpperCase().formatHex(b);
    }
}
