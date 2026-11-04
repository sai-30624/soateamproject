package com.payvault.user.service;

import com.payvault.user.dto.UserDtos.CreateUserRequest;
import com.payvault.user.dto.UserDtos.UpdateUserRequest;
import com.payvault.user.entity.User;
import com.payvault.user.exception.ApiException;
import com.payvault.user.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {

    private final UserRepository users;

    public UserService(UserRepository users) {
        this.users = users;
    }

    @Transactional
    public User create(CreateUserRequest req) {
        String email = req.email().trim().toLowerCase();
        if (users.existsByEmailIgnoreCase(email)) {
            throw new ApiException(HttpStatus.CONFLICT, "Email already registered");
        }
        if (req.phone() != null && users.existsByPhone(req.phone())) {
            throw new ApiException(HttpStatus.CONFLICT, "Phone number already registered");
        }
        User u = new User();
        u.setFullName(req.fullName().trim());
        u.setEmail(email);
        u.setPhone(req.phone());
        return users.save(u);
    }

    public User get(Long id) {
        return users.findById(id).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "User not found"));
    }

    @Transactional
    public User update(Long id, UpdateUserRequest req) {
        User u = get(id);
        users.findByPhone(req.phone())
                .filter(other -> !other.getId().equals(id))
                .ifPresent(other -> {
                    throw new ApiException(HttpStatus.CONFLICT, "Phone number already registered");
                });
        u.setFullName(req.fullName().trim());
        u.setPhone(req.phone());
        return u;
    }

    public User lookup(String query) {
        String q = query == null ? "" : query.trim();
        if (q.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Enter a phone number or email");
        }
        return (q.contains("@") ? users.findByEmailIgnoreCase(q) : users.findByPhone(q))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "No PayVault user found for " + q));
    }

    @Transactional
    public User setStatus(Long id, String status) {
        if (!User.ACTIVE.equals(status) && !User.BLOCKED.equals(status)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Status must be ACTIVE or BLOCKED");
        }
        User u = get(id);
        u.setStatus(status);
        return u;
    }
}
