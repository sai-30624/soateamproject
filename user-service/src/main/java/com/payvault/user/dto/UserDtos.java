package com.payvault.user.dto;

import java.time.Instant;

import com.payvault.user.entity.User;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public final class UserDtos {
    private UserDtos() {
    }

    public record CreateUserRequest(
            @NotBlank @Size(max = 100) String fullName,
            @NotBlank @Email @Size(max = 120) String email,
            @Pattern(regexp = "^[0-9]{10}$", message = "must be 10 digits") String phone) {
    }

    public record UpdateUserRequest(
            @NotBlank @Size(max = 100) String fullName,
            @NotBlank @Pattern(regexp = "^[0-9]{10}$", message = "must be 10 digits") String phone) {
    }

    public record UserDto(Long id, String fullName, String email, String phone, String status, Instant createdAt) {
        public static UserDto from(User u) {
            return new UserDto(u.getId(), u.getFullName(), u.getEmail(), u.getPhone(), u.getStatus(), u.getCreatedAt());
        }
    }

    /** What a sender sees when searching for a receiver - no full email or phone leaks. */
    public record LookupDto(Long id, String fullName, String maskedPhone) {
        public static LookupDto from(User u) {
            String p = u.getPhone();
            String masked = (p == null || p.length() < 4) ? "" : "******" + p.substring(p.length() - 4);
            return new LookupDto(u.getId(), u.getFullName(), masked);
        }
    }
}
