package com.datingapp.repository;

import com.datingapp.entity.Match;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface MatchRepository extends JpaRepository<Match, Long> {

    @Query("""
            SELECT m FROM Match m JOIN FETCH m.user1 JOIN FETCH m.user2
            WHERE (m.user1.id = :userId OR m.user2.id = :userId) AND m.active = true
            ORDER BY m.matchedAt DESC
            """)
    List<Match> findActiveMatchesWithUsers(@Param("userId") Long userId);

    @Query("SELECT m FROM Match m JOIN FETCH m.user1 JOIN FETCH m.user2 WHERE m.id = :id")
    Optional<Match> findByIdWithUsers(@Param("id") Long id);

    /** Expects the ids in canonical order (user1Id < user2Id). */
    Optional<Match> findByUser1IdAndUser2Id(Long user1Id, Long user2Id);

    @Modifying
    @Query("DELETE FROM Match m WHERE m.user1.id = :userId OR m.user2.id = :userId")
    int deleteAllInvolvingUser(@Param("userId") Long userId);
}
