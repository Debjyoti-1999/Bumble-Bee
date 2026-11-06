package com.datingapp.dto;

public record SwipeResponse(boolean matched, Long matchId) {

    public static SwipeResponse noMatch() {
        return new SwipeResponse(false, null);
    }

    public static SwipeResponse matched(Long matchId) {
        return new SwipeResponse(true, matchId);
    }
}
