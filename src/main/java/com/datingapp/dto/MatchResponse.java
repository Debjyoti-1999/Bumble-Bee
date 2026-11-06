package com.datingapp.dto;

import com.datingapp.entity.Match;
import com.datingapp.entity.User;

import java.time.Instant;

public record MatchResponse(
        Long matchId,
        Instant matchedAt,
        boolean active,
        PublicProfileResponse matchedUser
) {
    public static MatchResponse of(Match match, User viewer) {
        return new MatchResponse(match.getId(), match.getMatchedAt(), match.isActive(),
                PublicProfileResponse.of(match.otherParticipant(viewer.getId()), viewer));
    }
}
