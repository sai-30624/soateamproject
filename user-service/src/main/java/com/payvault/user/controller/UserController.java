package com.payvault.user.controller;

import com.payvault.user.dto.UserDtos.LookupDto;
import com.payvault.user.dto.UserDtos.UpdateUserRequest;
import com.payvault.user.dto.UserDtos.UserDto;
import com.payvault.user.exception.ApiException;
import com.payvault.user.service.UserService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Public API - reached through the Gateway, which sets X-User-Id from the JWT. */
@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService service;

    public UserController(UserService service) {
        this.service = service;
    }

    @GetMapping("/me")
    public UserDto me(@RequestHeader("X-User-Id") Long userId) {
        return UserDto.from(service.get(userId));
    }

    @PutMapping("/me")
    public UserDto update(@RequestHeader("X-User-Id") Long userId, @Valid @RequestBody UpdateUserRequest req) {
        return UserDto.from(service.update(userId, req));
    }

    @GetMapping("/lookup")
    public LookupDto lookup(@RequestParam("query") String query) {
        return LookupDto.from(service.lookup(query));
    }

    @PutMapping("/{id}/status")
    public UserDto setStatus(@RequestHeader(value = "X-User-Role", required = false) String role,
                             @PathVariable("id") Long id, @RequestParam("value") String value) {
        if (!"ADMIN".equals(role)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Admin only");
        }
        return UserDto.from(service.setStatus(id, value.toUpperCase()));
    }
}
