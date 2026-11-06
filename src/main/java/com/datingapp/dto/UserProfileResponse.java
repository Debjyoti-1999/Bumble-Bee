package com.datingapp.dto;

import com.datingapp.entity.Gender;
import com.datingapp.entity.User;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

/** The caller's own profile, including private fields such as email and exact location. */
public record UserProfileResponse(
        Long id,
        String email,
        String name,
        LocalDate dateOfBirth,
        int age,
        Gender gender,
        String bio,
        String profilePictureUrl,
        double latitude,
        double longitude,
        List<String> interests,
        int maxDistanceKm,
        int minAge,
        int maxAge,
        Gender preferredGender,
        Instant createdAt,
        Instant lastActive
) {
    public static UserProfileResponse from(User u) {
        return new UserProfileResponse(
                u.getId(), u.getEmail(), u.getName(), u.getDateOfBirth(),
                u.ageOn(LocalDate.now(ZoneOffset.UTC)), u.getGender(), u.getBio(),
                u.getProfilePictureUrl(), u.getLatitude(), u.getLongitude(),
                u.getInterests().stream().sorted().toList(),
                u.getMaxDistanceKm(), u.getMinAge(), u.getMaxAge(), u.getPreferredGender(),
                u.getCreatedAt(), u.getLastActive());
    }
}
