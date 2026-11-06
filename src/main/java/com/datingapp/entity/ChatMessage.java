package com.datingapp.entity;

import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "chat_messages")
public class ChatMessage {

    public static final int MAX_CONTENT_LENGTH = 2000;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "match_id", nullable = false)
    private Match match;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sender_id", nullable = false)
    private User sender;

    @Column(nullable = false, length = MAX_CONTENT_LENGTH)
    private String content;

    @Column(nullable = false, updatable = false)
    private Instant sentAt;

    // "read" is a reserved word in MySQL, so map it to a safe column name.
    @Column(name = "is_read", nullable = false)
    private boolean read = false;

    protected ChatMessage() {
    }

    public ChatMessage(Match match, User sender, String content) {
        this.match = match;
        this.sender = sender;
        this.content = content;
    }

    @PrePersist
    protected void onCreate() {
        this.sentAt = Instant.now();
    }

    public Long getId() { return id; }
    public Match getMatch() { return match; }
    public User getSender() { return sender; }
    public String getContent() { return content; }
    public Instant getSentAt() { return sentAt; }
    public boolean isRead() { return read; }
}
