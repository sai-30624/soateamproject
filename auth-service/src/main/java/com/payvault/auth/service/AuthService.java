package com.payvault.auth.service;

import com.payvault.auth.client.UserClient;
import com.payvault.auth.client.WalletClient;
import com.payvault.auth.dto.AuthDtos.AuthResponse;
import com.payvault.auth.dto.AuthDtos.CreateUserRequest;
import com.payvault.auth.dto.AuthDtos.CreateWalletRequest;
import com.payvault.auth.dto.AuthDtos.LoginRequest;
import com.payvault.auth.dto.AuthDtos.RegisterRequest;
import com.payvault.auth.dto.AuthDtos.UserDto;
import com.payvault.auth.entity.Credential;
import com.payvault.auth.exception.ApiException;
import com.payvault.auth.exception.FeignErrors;
import com.payvault.auth.repository.CredentialRepository;
import feign.FeignException;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final CredentialRepository credentials;
    private final PasswordEncoder encoder;
    private final JwtService jwt;
    private final UserClient userClient;
    private final WalletClient walletClient;

    public AuthService(CredentialRepository credentials, PasswordEncoder encoder, JwtService jwt,
                       UserClient userClient, WalletClient walletClient) {
        this.credentials = credentials;
        this.encoder = encoder;
        this.jwt = jwt;
        this.userClient = userClient;
        this.walletClient = walletClient;
    }

    /** Register = create profile (User Service) + credentials (here) + empty wallet (Wallet Service). */
    public AuthResponse register(RegisterRequest req) {
        String email = req.email().trim().toLowerCase();
        if (credentials.existsByEmailIgnoreCase(email)) {
            throw new ApiException(HttpStatus.CONFLICT, "Email already registered");
        }

        UserDto user;
        try {
            user = userClient.create(new CreateUserRequest(req.fullName().trim(), email, req.phone()));
        } catch (FeignException fe) {
            throw FeignErrors.toApi(fe);
        }

        Credential c = new Credential();
        c.setUserId(user.id());
        c.setEmail(email);
        c.setPasswordHash(encoder.encode(req.password()));
        credentials.save(c);

        try {
            walletClient.create(new CreateWalletRequest(user.id()));
        } catch (FeignException fe) {
            // Not fatal: Wallet Service creates the wallet lazily on first access.
            log.warn("Wallet creation deferred for user {}: {}", user.id(), fe.getMessage());
        }
        return tokens(c);
    }

    public AuthResponse login(LoginRequest req) {
        Credential c = credentials.findByEmailIgnoreCase(req.email().trim())
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Invalid email or password"));
        if (!encoder.matches(req.password(), c.getPasswordHash())) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Invalid email or password");
        }
        return tokens(c);
    }

    public AuthResponse refresh(String refreshToken) {
        try {
            Claims claims = jwt.parse(refreshToken);
            if (!"refresh".equals(claims.get("type", String.class))) {
                throw new ApiException(HttpStatus.UNAUTHORIZED, "Not a refresh token");
            }
            Credential c = credentials.findByUserId(Long.valueOf(claims.getSubject()))
                    .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Unknown user"));
            return tokens(c);
        } catch (JwtException | IllegalArgumentException e) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Invalid or expired refresh token");
        }
    }

    private AuthResponse tokens(Credential c) {
        return new AuthResponse(jwt.accessToken(c), jwt.refreshToken(c), c.getUserId(),
                c.getEmail(), c.getRole(), jwt.accessTtlSeconds());
    }
}
