package com.datingapp.entity;

import jakarta.persistence.*;

import java.time.Instant;

/**
 * A mutual like between two users. The pair is always stored with the lower user id in
 * {@code user1} so the unique constraint on (user1_id, user2_id) covers both orderings.
 */
@Entity
@Table(name = "matches", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"user1_id", "user2_id"})
})
public class Match {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user1_id", nullable = false)
    private User user1;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user2_id", nullable = false)
    private User user2;

    @Column(nullable = false, updatable = false)
    private Instant matchedAt;

    @Column(nullable = false)
    private boolean active = true;

    protected Match() {
    }

    public static Match between(User a, User b) {
        Match match = new Match();
        boolean aFirst = a.getId() < b.getId();
        match.user1 = aFirst ? a : b;
        match.user2 = aFirst ? b : a;
        return match;
    }

    @PrePersist
    protected void onCreate() {
        this.matchedAt = Instant.now();
    }

    public boolean involves(Long userId) {
        return user1.getId().equals(userId) || user2.getId().equals(userId);
    }

    /** Returns the participant that is not {@code userId}. Caller must ensure {@link #involves}. */
    public User otherParticipant(Long userId) {
        return user1.getId().equals(userId) ? user2 : user1;
    }

    public Long getId() { return id; }
    public User getUser1() { return user1; }
    public User getUser2() { return user2; }
    public Instant getMatchedAt() { return matchedAt; }

    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
}
