package com.flowboard.web.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        // Letters (any language), digits and _ . - ; spaces are not allowed, so no trimming is needed.
        @NotBlank
        @Pattern(regexp = "^[\\p{L}\\p{N}_.-]{3,50}$",
                message = "must be 3-50 characters: letters, digits, '_', '.', '-'")
        String username,

        @NotBlank @Email @Size(max = 254)
        String email,

        // BCrypt only uses the first 72 bytes of a password, so longer input is rejected up front.
        @NotBlank @Size(min = 8, max = 72)
        String password
) {
}
