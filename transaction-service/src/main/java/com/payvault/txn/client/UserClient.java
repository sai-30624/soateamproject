package com.payvault.txn.client;

import com.payvault.txn.dto.TxnDtos.UserDto;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "user-service", path = "/internal/users")
public interface UserClient {
    @GetMapping("/{id}")
    UserDto get(@PathVariable("id") Long id);
}
