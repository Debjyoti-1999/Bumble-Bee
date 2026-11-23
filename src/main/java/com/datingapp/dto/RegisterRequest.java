package com.datingapp.dto;

import com.datingapp.entity.Gender;
import jakarta.validation.constraints.*;

import java.time.LocalDate;
import java.util.Set;

public record RegisterRequest(
        @NotBlank @Email @Size(max = 254) String email,
        // BCrypt only uses the first 72 bytes of a password.
        @NotBlank @Size(min = 8, max = 72) String password,
        @NotBlank @Size(max = 100) String name,
        @NotNull @Past LocalDate dateOfBirth,
        @NotNull Gender gender,
        @Size(max = 1000) String bio,
        @Size(max = 2048) @Pattern(regexp = "^https://\\S+$", message = "must be an https URL") String profilePictureUrl,
        @NotNull @DecimalMin("-90.0") @DecimalMax("90.0") Double latitude,
        @NotNull @DecimalMin("-180.0") @DecimalMax("180.0") Double longitude,
        @Size(max = 20) Set<@NotBlank @Size(max = 50) String> interests,
        @Min(1) @Max(500) Integer maxDistanceKm,
        @Min(18) @Max(100) Integer minAge,
        @Min(18) @Max(100) Integer maxAge,
        Gender preferredGender
) {
}
