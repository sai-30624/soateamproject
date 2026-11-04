package com.payvault.txn.client;

import java.util.Map;

import com.payvault.txn.dto.TxnDtos.MoneyRequest;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/** Wallet Service is found through Eureka by its name. */
@FeignClient(name = "wallet-service", path = "/internal/wallets")
public interface WalletClient {

    @PostMapping
    Map<String, Object> create(@RequestBody Map<String, Object> request);

    @PostMapping("/{userId}/debit")
    Map<String, Object> debit(@PathVariable("userId") Long userId, @RequestBody MoneyRequest request);

    @PostMapping("/{userId}/credit")
    Map<String, Object> credit(@PathVariable("userId") Long userId, @RequestBody MoneyRequest request);
}
