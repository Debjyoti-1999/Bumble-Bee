package com.datingapp.dto;

import com.datingapp.entity.Gender;
import jakarta.validation.constraints.*;

import java.util.Set;

/** Partial update: null fields are left unchanged. */
public record UpdateProfileRequest(
        @Size(min = 1, max = 100) @Pattern(regexp = ".*\\S.*", message = "must not be blank") String name,
        @Size(max = 1000) String bio,
        @Size(max = 2048) @Pattern(regexp = "^https://\\S+$", message = "must be an https URL") String profilePictureUrl,
        @DecimalMin("-90.0") @DecimalMax("90.0") Double latitude,
        @DecimalMin("-180.0") @DecimalMax("180.0") Double longitude,
        @Size(max = 20) Set<@NotBlank @Size(max = 50) String> interests,
        @Min(1) @Max(500) Integer maxDistanceKm,
        @Min(18) @Max(100) Integer minAge,
        @Min(18) @Max(100) Integer maxAge,
        Gender preferredGender
) {
}
