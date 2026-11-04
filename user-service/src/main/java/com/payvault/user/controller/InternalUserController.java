package com.payvault.user.controller;

import com.payvault.user.dto.UserDtos.CreateUserRequest;
import com.payvault.user.dto.UserDtos.UserDto;
import com.payvault.user.service.UserService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Service-to-service API. The Gateway refuses /internal/** so clients can never call it. */
@RestController
@RequestMapping("/internal/users")
public class InternalUserController {

    private final UserService service;

    public InternalUserController(UserService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public UserDto create(@Valid @RequestBody CreateUserRequest req) {
        return UserDto.from(service.create(req));
    }

    @GetMapping("/{id}")
    public UserDto get(@PathVariable("id") Long id) {
        return UserDto.from(service.get(id));
    }
}
