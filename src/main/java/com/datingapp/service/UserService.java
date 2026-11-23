package com.datingapp.service;

import com.datingapp.dto.PublicProfileResponse;
import com.datingapp.dto.RegisterRequest;
import com.datingapp.dto.UpdateProfileRequest;
import com.datingapp.dto.UserProfileResponse;
import com.datingapp.entity.User;
import com.datingapp.exception.BadRequestException;
import com.datingapp.exception.NotFoundException;
import com.datingapp.repository.ChatMessageRepository;
import com.datingapp.repository.MatchRepository;
import com.datingapp.repository.SwipeRepository;
import com.datingapp.repository.UserRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;

@Service
@Transactional(readOnly = true)
public class UserService {

    public static final int MINIMUM_USER_AGE = 18;

    private final UserRepository userRepository;
    private final SwipeRepository swipeRepository;
    private final MatchRepository matchRepository;
    private final ChatMessageRepository chatMessageRepository;

    public UserService(UserRepository userRepository,
                       SwipeRepository swipeRepository,
                       MatchRepository matchRepository,
                       ChatMessageRepository chatMessageRepository) {
        this.userRepository = userRepository;
        this.swipeRepository = swipeRepository;
        this.matchRepository = matchRepository;
        this.chatMessageRepository = chatMessageRepository;
    }

    public static String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    /** Creates the account; the caller is responsible for hashing the password. */
    @Transactional
    public User createAccount(RegisterRequest req, String passwordHash) {
        if (req.dateOfBirth().plusYears(MINIMUM_USER_AGE).isAfter(today())) {
            throw new BadRequestException("You must be at least " + MINIMUM_USER_AGE + " years old");
        }

        User user = new User();
        user.setEmail(normalizeEmail(req.email()));
        user.setPasswordHash(passwordHash);
        user.setName(req.name().trim());
        user.setDateOfBirth(req.dateOfBirth());
        user.setGender(req.gender());
        user.setBio(req.bio());
        user.setProfilePictureUrl(req.profilePictureUrl());
        user.setLatitude(req.latitude());
        user.setLongitude(req.longitude());
        if (req.interests() != null) user.setInterests(normalizeInterests(req.interests()));
        if (req.maxDistanceKm() != null) user.setMaxDistanceKm(req.maxDistanceKm());
        if (req.minAge() != null) user.setMinAge(req.minAge());
        if (req.maxAge() != null) user.setMaxAge(req.maxAge());
        user.setPreferredGender(req.preferredGender());
        validateAgeRange(user);

        return userRepository.save(user);
    }

    public UserProfileResponse getOwnProfile(Long userId) {
        return UserProfileResponse.from(requireUser(userId));
    }

    public PublicProfileResponse getPublicProfile(Long viewerId, Long userId) {
        User viewer = requireUser(viewerId);
        User subject = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User not found"));
        return PublicProfileResponse.of(subject, viewer);
    }

    @Transactional
    public UserProfileResponse updateProfile(Long userId, UpdateProfileRequest req) {
        User user = requireUser(userId);

        if (req.name() != null) user.setName(req.name().trim());
        if (req.bio() != null) user.setBio(req.bio());
        if (req.profilePictureUrl() != null) user.setProfilePictureUrl(req.profilePictureUrl());
        if (req.latitude() != null) user.setLatitude(req.latitude());
        if (req.longitude() != null) user.setLongitude(req.longitude());
        if (req.interests() != null) user.setInterests(normalizeInterests(req.interests()));
        if (req.maxDistanceKm() != null) user.setMaxDistanceKm(req.maxDistanceKm());
        if (req.minAge() != null) user.setMinAge(req.minAge());
        if (req.maxAge() != null) user.setMaxAge(req.maxAge());
        if (req.preferredGender() != null) user.setPreferredGender(req.preferredGender());
        validateAgeRange(user);

        user.setLastActive(Instant.now());
        return UserProfileResponse.from(user);
    }

    /**
     * Candidates matching both users' gender, age and distance preferences, excluding anyone the
     * caller has already swiped on. Antimeridian wrap-around is not handled (the bounding box is
     * simply clamped), which only affects users within a few hundred km of longitude ±180.
     */
    public List<PublicProfileResponse> getPotentialMatches(Long userId, int page, int size) {
        User me = requireUser(userId);
        LocalDate today = today();

        // Born on or before maxDob => at least minAge; born on or after minDob => at most maxAge.
        LocalDate maxDob = today.minusYears(me.getMinAge());
        LocalDate minDob = today.minusYears(me.getMaxAge() + 1L).plusDays(1);

        double lat = me.getLatitude();
        double lon = me.getLongitude();
        double latDelta = me.getMaxDistanceKm() / GeoUtils.KM_PER_DEGREE;
        double cosLat = Math.max(Math.cos(Math.toRadians(lat)), 0.01);
        double lonDelta = latDelta / cosLat;

        List<User> candidates = userRepository.findCandidates(
                userId, me.getGender(), me.getPreferredGender(), me.ageOn(today), minDob, maxDob,
                lat, lon, cosLat,
                Math.max(-90, lat - latDelta), Math.min(90, lat + latDelta),
                Math.max(-180, lon - lonDelta), Math.min(180, lon + lonDelta),
                me.getMaxDistanceKm(), GeoUtils.KM_PER_DEGREE,
                PageRequest.of(page, size));

        return candidates.stream().map(c -> PublicProfileResponse.of(c, me)).toList();
    }

    @Transactional
    public void touchLastActive(Long userId) {
        userRepository.updateLastActive(userId, Instant.now());
    }

    /** Hard-deletes the account together with its swipes, matches and their messages. */
    @Transactional
    public void deleteAccount(Long userId) {
        User user = requireUser(userId);
        chatMessageRepository.deleteAllInMatchesOfUser(userId);
        matchRepository.deleteAllInvolvingUser(userId);
        swipeRepository.deleteAllInvolvingUser(userId);
        userRepository.delete(user);
    }

    User requireUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User not found"));
    }

    private static void validateAgeRange(User user) {
        if (user.getMinAge() > user.getMaxAge()) {
            throw new BadRequestException("minAge must be less than or equal to maxAge");
        }
    }

    private static Set<String> normalizeInterests(Set<String> interests) {
        Set<String> normalized = new TreeSet<>();
        interests.forEach(i -> normalized.add(i.trim().toLowerCase(Locale.ROOT)));
        return normalized;
    }

    private static LocalDate today() {
        return LocalDate.now(ZoneOffset.UTC);
    }
}
