package com.datingapp.event;

/** Published inside the swipe transaction; listeners act after commit. */
public record MatchCreatedEvent(
        Long matchId,
        Long user1Id,
        String user1Name,
        Long user2Id,
        String user2Name
) {
}
