package com.byarrival.domain.admin.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * @param password BCrypt가 72바이트까지만 쓰므로 그보다 긴 값은 받지 않는다
 */
public record AdminLoginRequest(
    @NotBlank @Email @Size(max = 254) String email,
    @NotBlank @Size(max = 72) String password
) {

    @Override
    public String toString() {
        return "AdminLoginRequest[email=" + email + "]";
    }
}
