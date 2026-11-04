package com.payvault.auth.client;

import com.payvault.auth.dto.AuthDtos.CreateUserRequest;
import com.payvault.auth.dto.AuthDtos.UserDto;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/** "user-service" is resolved through Eureka - no host or port is hard-coded. */
@FeignClient(name = "user-service", path = "/internal/users")
public interface UserClient {
    @PostMapping
    UserDto create(@RequestBody CreateUserRequest request);
}
