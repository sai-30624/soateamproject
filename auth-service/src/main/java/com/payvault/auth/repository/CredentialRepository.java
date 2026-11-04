package com.payvault.auth.repository;

import java.util.Optional;

import com.payvault.auth.entity.Credential;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CredentialRepository extends JpaRepository<Credential, Long> {
    Optional<Credential> findByEmailIgnoreCase(String email);
    Optional<Credential> findByUserId(Long userId);
    boolean existsByEmailIgnoreCase(String email);
}
