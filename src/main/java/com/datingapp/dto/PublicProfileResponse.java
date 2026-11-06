package com.datingapp.dto;

import com.datingapp.entity.Gender;
import com.datingapp.entity.User;
import com.datingapp.service.GeoUtils;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

/**
 * What other users may see. Deliberately excludes email, date of birth, exact coordinates and
 * preferences; location is exposed only as a whole-kilometre distance from the viewer.
 */
public record PublicProfileResponse(
        Long id,
        String name,
        int age,
        Gender gender,
        String bio,
        String profilePictureUrl,
        List<String> interests,
        int distanceKm
) {
    public static PublicProfileResponse of(User subject, User viewer) {
        return new PublicProfileResponse(
                subject.getId(), subject.getName(), subject.ageOn(LocalDate.now(ZoneOffset.UTC)),
                subject.getGender(), subject.getBio(), subject.getProfilePictureUrl(),
                subject.getInterests().stream().sorted().toList(),
                GeoUtils.roundedDistanceKm(viewer.getLatitude(), viewer.getLongitude(),
                        subject.getLatitude(), subject.getLongitude()));
    }
}
