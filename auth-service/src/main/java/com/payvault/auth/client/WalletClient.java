package com.payvault.auth.client;

import java.util.Map;

import com.payvault.auth.dto.AuthDtos.CreateWalletRequest;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = "wallet-service", path = "/internal/wallets")
public interface WalletClient {
    @PostMapping
    Map<String, Object> create(@RequestBody CreateWalletRequest request);
}
