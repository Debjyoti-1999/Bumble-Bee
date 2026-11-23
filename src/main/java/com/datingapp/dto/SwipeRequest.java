package com.datingapp.dto;

import jakarta.validation.constraints.NotNull;

/** The swiper is always the authenticated user. */
public record SwipeRequest(
        @NotNull Long swipedId,
        @NotNull Boolean liked
) {
}
