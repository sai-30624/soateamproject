package com.payvault.wallet.controller;

import com.payvault.wallet.dto.WalletDtos.CreateWalletRequest;
import com.payvault.wallet.dto.WalletDtos.LedgerResult;
import com.payvault.wallet.dto.WalletDtos.MoneyRequest;
import com.payvault.wallet.dto.WalletDtos.WalletDto;
import com.payvault.wallet.service.WalletService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Called only by other services (Auth, Transaction). Blocked at the Gateway. */
@RestController
@RequestMapping("/internal/wallets")
public class InternalWalletController {

    private final WalletService service;

    public InternalWalletController(WalletService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public WalletDto create(@Valid @RequestBody CreateWalletRequest req) {
        return WalletDto.from(service.create(req.userId()));
    }

    @GetMapping("/{userId}")
    public WalletDto get(@PathVariable("userId") Long userId) {
        return WalletDto.from(service.get(userId));
    }

    @PostMapping("/{userId}/debit")
    public LedgerResult debit(@PathVariable("userId") Long userId, @Valid @RequestBody MoneyRequest req) {
        return service.debit(userId, req.txnRef(), req.amount());
    }

    @PostMapping("/{userId}/credit")
    public LedgerResult credit(@PathVariable("userId") Long userId, @Valid @RequestBody MoneyRequest req) {
        return service.credit(userId, req.txnRef(), req.amount());
    }
}
