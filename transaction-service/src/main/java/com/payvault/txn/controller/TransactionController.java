package com.payvault.txn.controller;

import java.util.Map;

import com.payvault.txn.client.WalletGateway;
import com.payvault.txn.dto.TxnDtos.DepositRequest;
import com.payvault.txn.dto.TxnDtos.TransferRequest;
import com.payvault.txn.dto.TxnDtos.TxnResponse;
import com.payvault.txn.entity.Transaction;
import com.payvault.txn.service.TransactionService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/transactions")
public class TransactionController {

    private final TransactionService service;
    private final WalletGateway walletGateway;

    public TransactionController(TransactionService service, WalletGateway walletGateway) {
        this.service = service;
        this.walletGateway = walletGateway;
    }

    @PostMapping("/transfer")
    public ResponseEntity<TxnResponse> transfer(@RequestHeader("X-User-Id") Long userId,
                                                @RequestHeader("Idempotency-Key") String key,
                                                @Valid @RequestBody TransferRequest req) {
        return respond(service.transfer(userId, key, req), userId);
    }

    @PostMapping("/deposit")
    public ResponseEntity<TxnResponse> deposit(@RequestHeader("X-User-Id") Long userId,
                                               @RequestHeader("Idempotency-Key") String key,
                                               @Valid @RequestBody DepositRequest req) {
        return respond(service.deposit(userId, key, req), userId);
    }

    @GetMapping
    public Map<String, Object> history(@RequestHeader("X-User-Id") Long userId,
                                       @RequestParam(defaultValue = "0") int page,
                                       @RequestParam(defaultValue = "20") int size) {
        Page<Transaction> p = service.history(userId, page, size);
        return Map.of(
                "content", p.getContent().stream().map(t -> TxnResponse.of(t, userId)).toList(),
                "page", p.getNumber(),
                "totalPages", p.getTotalPages(),
                "totalElements", p.getTotalElements());
    }

    /** Live state of the Wallet circuit breaker - handy for the fault-tolerance demo. */
    @GetMapping("/circuit")
    public Map<String, Object> circuit() {
        return walletGateway.state();
    }

    @GetMapping("/{txnRef}")
    public TxnResponse one(@RequestHeader("X-User-Id") Long userId, @PathVariable("txnRef") String txnRef) {
        return TxnResponse.of(service.getForUser(txnRef, userId), userId);
    }

    private static ResponseEntity<TxnResponse> respond(Transaction t, Long viewer) {
        HttpStatus status = switch (t.getStatus()) {
            case SUCCESS, REVERSED -> HttpStatus.OK;
            case FAILED -> HttpStatus.UNPROCESSABLE_ENTITY;
            default -> HttpStatus.ACCEPTED; // still in progress, recovery job will finish it
        };
        return ResponseEntity.status(status).body(TxnResponse.of(t, viewer));
    }
}
