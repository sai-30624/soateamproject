package com.payvault.wallet.controller;

import java.util.Map;

import com.payvault.wallet.dto.WalletDtos.LedgerDto;
import com.payvault.wallet.dto.WalletDtos.ReconcileDto;
import com.payvault.wallet.dto.WalletDtos.WalletDto;
import com.payvault.wallet.entity.LedgerEntry;
import com.payvault.wallet.service.WalletService;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/wallets")
public class WalletController {

    private final WalletService service;

    public WalletController(WalletService service) {
        this.service = service;
    }

    @GetMapping("/me")
    public WalletDto me(@RequestHeader("X-User-Id") Long userId) {
        return WalletDto.from(service.getOrCreate(userId));
    }

    @GetMapping("/me/ledger")
    public Map<String, Object> ledger(@RequestHeader("X-User-Id") Long userId,
                                      @RequestParam(defaultValue = "0") int page,
                                      @RequestParam(defaultValue = "20") int size) {
        Page<LedgerEntry> p = service.statement(userId, page, size);
        return Map.of(
                "content", p.getContent().stream().map(LedgerDto::from).toList(),
                "page", p.getNumber(),
                "totalPages", p.getTotalPages(),
                "totalElements", p.getTotalElements());
    }

    @GetMapping("/me/reconcile")
    public ReconcileDto reconcile(@RequestHeader("X-User-Id") Long userId) {
        return service.reconcile(userId);
    }
}
