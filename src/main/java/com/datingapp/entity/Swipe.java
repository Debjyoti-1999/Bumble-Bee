package com.datingapp.entity;

import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "swipes", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"swiper_id", "swiped_id"})
})
public class Swipe {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "swiper_id", nullable = false)
    private User swiper;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "swiped_id", nullable = false)
    private User swiped;

    @Column(nullable = false)
    private boolean liked;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    protected Swipe() {
    }

    public Swipe(User swiper, User swiped, boolean liked) {
        this.swiper = swiper;
        this.swiped = swiped;
        this.liked = liked;
    }

    @PrePersist
    protected void onCreate() {
        this.createdAt = Instant.now();
    }

    public Long getId() { return id; }
    public User getSwiper() { return swiper; }
    public User getSwiped() { return swiped; }
    public boolean isLiked() { return liked; }
    public Instant getCreatedAt() { return createdAt; }
}
