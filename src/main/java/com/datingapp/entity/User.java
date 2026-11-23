package com.datingapp.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.time.LocalDate;
import java.time.Period;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "users")
public class User {

    public static final int DEFAULT_MAX_DISTANCE_KM = 50;
    public static final int DEFAULT_MIN_AGE = 18;
    public static final int DEFAULT_MAX_AGE = 60;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 254)
    private String email;

    @Column(nullable = false, length = 100)
    private String passwordHash;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false)
    private LocalDate dateOfBirth;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20)
    private Gender gender;

    @Column(length = 1000)
    private String bio;

    @Column(length = 2048)
    private String profilePictureUrl;

    @Column(nullable = false)
    private double latitude;

    @Column(nullable = false)
    private double longitude;

    @ElementCollection
    @CollectionTable(name = "user_interests", joinColumns = @JoinColumn(name = "user_id"))
    @Column(name = "interest", nullable = false, length = 50)
    private Set<String> interests = new HashSet<>();

    @Column(nullable = false)
    private int maxDistanceKm = DEFAULT_MAX_DISTANCE_KM;

    @Column(nullable = false)
    private int minAge = DEFAULT_MIN_AGE;

    @Column(nullable = false)
    private int maxAge = DEFAULT_MAX_AGE;

    /** Null means "no preference". */
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(length = 20)
    private Gender preferredGender;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant lastActive;

    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.lastActive = now;
    }

    public int ageOn(LocalDate date) {
        return Period.between(dateOfBirth, date).getYears();
    }

    public Long getId() { return id; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public LocalDate getDateOfBirth() { return dateOfBirth; }
    public void setDateOfBirth(LocalDate dateOfBirth) { this.dateOfBirth = dateOfBirth; }

    public Gender getGender() { return gender; }
    public void setGender(Gender gender) { this.gender = gender; }

    public String getBio() { return bio; }
    public void setBio(String bio) { this.bio = bio; }

    public String getProfilePictureUrl() { return profilePictureUrl; }
    public void setProfilePictureUrl(String profilePictureUrl) { this.profilePictureUrl = profilePictureUrl; }

    public double getLatitude() { return latitude; }
    public void setLatitude(double latitude) { this.latitude = latitude; }

    public double getLongitude() { return longitude; }
    public void setLongitude(double longitude) { this.longitude = longitude; }

    public Set<String> getInterests() { return interests; }
    public void setInterests(Set<String> interests) {
        this.interests.clear();
        this.interests.addAll(interests);
    }

    public int getMaxDistanceKm() { return maxDistanceKm; }
    public void setMaxDistanceKm(int maxDistanceKm) { this.maxDistanceKm = maxDistanceKm; }

    public int getMinAge() { return minAge; }
    public void setMinAge(int minAge) { this.minAge = minAge; }

    public int getMaxAge() { return maxAge; }
    public void setMaxAge(int maxAge) { this.maxAge = maxAge; }

    public Gender getPreferredGender() { return preferredGender; }
    public void setPreferredGender(Gender preferredGender) { this.preferredGender = preferredGender; }

    public Instant getCreatedAt() { return createdAt; }

    public Instant getLastActive() { return lastActive; }
    public void setLastActive(Instant lastActive) { this.lastActive = lastActive; }
}
