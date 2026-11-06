package com.datingapp.repository;

import com.datingapp.entity.Gender;
import com.datingapp.entity.User;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    /** Row lock used to serialize swipes between a pair of users. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT u FROM User u WHERE u.id = :id")
    Optional<User> findByIdForUpdate(@Param("id") Long id);

    @Modifying
    @Query("UPDATE User u SET u.lastActive = :now WHERE u.id = :id")
    int updateLastActive(@Param("id") Long id, @Param("now") Instant now);

    /**
     * Candidates that satisfy both users' preferences (gender, age, distance) and that the
     * searcher has not swiped on yet. Distance uses an equirectangular approximation, which is
     * accurate to well under 1% at dating-app radii (<= 500 km); the bounding box lets the
     * database use the location index before the per-row distance check.
     */
    @Query("""
            SELECT u FROM User u
            WHERE u.id <> :userId
              AND u.id NOT IN (SELECT s.swiped.id FROM Swipe s WHERE s.swiper.id = :userId)
              AND (:preferredGender IS NULL OR u.gender = :preferredGender)
              AND (u.preferredGender IS NULL OR u.preferredGender = :myGender)
              AND u.dateOfBirth BETWEEN :minDob AND :maxDob
              AND :myAge BETWEEN u.minAge AND u.maxAge
              AND u.latitude BETWEEN :minLat AND :maxLat
              AND u.longitude BETWEEN :minLon AND :maxLon
              AND power(u.latitude - :lat, 2) + power((u.longitude - :lon) * :cosLat, 2)
                  <= power(least(:radiusKm, cast(u.maxDistanceKm as Double)) / :kmPerDegree, 2)
            ORDER BY u.lastActive DESC, u.id
            """)
    List<User> findCandidates(@Param("userId") Long userId,
                              @Param("myGender") Gender myGender,
                              @Param("preferredGender") Gender preferredGender,
                              @Param("myAge") int myAge,
                              @Param("minDob") LocalDate minDob,
                              @Param("maxDob") LocalDate maxDob,
                              @Param("lat") double lat,
                              @Param("lon") double lon,
                              @Param("cosLat") double cosLat,
                              @Param("minLat") double minLat,
                              @Param("maxLat") double maxLat,
                              @Param("minLon") double minLon,
                              @Param("maxLon") double maxLon,
                              @Param("radiusKm") double radiusKm,
                              @Param("kmPerDegree") double kmPerDegree,
                              Pageable pageable);
}
