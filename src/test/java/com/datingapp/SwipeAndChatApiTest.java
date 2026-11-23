package com.datingapp;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SwipeAndChatApiTest extends ApiTestSupport {

    @Test
    void mutualLikeCreatesMatchVisibleToBothUsers() throws Exception {
        TestUser alice = register("Alice");
        TestUser bob = register("Bob");

        assertThat(swipe(alice, bob, true).get("matched").asBoolean()).isFalse();
        JsonNode second = swipe(bob, alice, true);
        assertThat(second.get("matched").asBoolean()).isTrue();
        long matchId = second.get("matchId").asLong();

        perform(get("/api/matches"), alice)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].matchId").value(matchId))
                .andExpect(jsonPath("$[0].matchedUser.name").value("Bob"))
                .andExpect(jsonPath("$[0].matchedUser.latitude").doesNotExist());
        perform(get("/api/matches/{id}", matchId), bob)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.matchedUser.name").value("Alice"));
    }

    @Test
    void dislikeDoesNotMatch() throws Exception {
        TestUser a = register("A");
        TestUser b = register("B");
        swipe(a, b, false);
        assertThat(swipe(b, a, true).get("matched").asBoolean()).isFalse();
    }

    @Test
    void invalidSwipesAreRejectedWithClientErrors() throws Exception {
        TestUser a = register("Swiper");
        TestUser b = register("Target");

        performJson(post("/api/swipes"), a, Map.of("swipedId", a.id(), "liked", true))
                .andExpect(status().isBadRequest());
        performJson(post("/api/swipes"), a, Map.of("swipedId", 999_999, "liked", true))
                .andExpect(status().isNotFound());
        performJson(post("/api/swipes"), a, Map.of("swipedId", b.id()))
                .andExpect(status().isBadRequest());

        swipe(a, b, true);
        performJson(post("/api/swipes"), a, Map.of("swipedId", b.id(), "liked", true))
                .andExpect(status().isConflict());
    }

    @Test
    void nonParticipantsCannotSeeOrUseAMatch() throws Exception {
        TestUser a = register("A");
        TestUser b = register("B");
        TestUser outsider = register("Outsider");
        long matchId = createMatch(a, b);

        perform(get("/api/matches/{id}", matchId), outsider).andExpect(status().isNotFound());
        perform(get("/api/matches/{id}/messages", matchId), outsider).andExpect(status().isNotFound());
        performJson(post("/api/matches/{id}/messages", matchId), outsider, Map.of("content", "hi"))
                .andExpect(status().isNotFound());
        perform(post("/api/matches/{id}/unmatch", matchId), outsider).andExpect(status().isNotFound());
        perform(post("/api/matches/{id}/messages/read", matchId), outsider).andExpect(status().isNotFound());
    }

    @Test
    void messageHistoryIsChronologicalAndCursorPaginated() throws Exception {
        TestUser a = register("A");
        TestUser b = register("B");
        long matchId = createMatch(a, b);
        long first = sendMessage(a, matchId, "one").get("id").asLong();
        sendMessage(b, matchId, "two");
        long third = sendMessage(a, matchId, "three").get("id").asLong();

        perform(get("/api/matches/{id}/messages", matchId).param("size", "2"), b)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].content").value("two"))
                .andExpect(jsonPath("$[1].content").value("three"))
                .andExpect(jsonPath("$[1].id").value(third))
                .andExpect(jsonPath("$[1].senderId").value(a.id()));

        long oldestOnPage = third - 1;
        perform(get("/api/matches/{id}/messages", matchId).param("beforeId", String.valueOf(oldestOnPage)), b)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(first));
    }

    @Test
    void markAsReadOnlyAffectsMessagesFromTheOtherUser() throws Exception {
        TestUser a = register("A");
        TestUser b = register("B");
        long matchId = createMatch(a, b);
        sendMessage(a, matchId, "from a");
        sendMessage(b, matchId, "from b");

        perform(post("/api/matches/{id}/messages/read", matchId), b).andExpect(status().isNoContent());

        perform(get("/api/matches/{id}/messages", matchId), a)
                .andExpect(jsonPath("$[0].content").value("from a"))
                .andExpect(jsonPath("$[0].read").value(true))
                .andExpect(jsonPath("$[1].content").value("from b"))
                .andExpect(jsonPath("$[1].read").value(false));
    }

    @Test
    void invalidMessagesAreRejected() throws Exception {
        TestUser a = register("A");
        TestUser b = register("B");
        long matchId = createMatch(a, b);

        performJson(post("/api/matches/{id}/messages", matchId), a, Map.of("content", "   "))
                .andExpect(status().isBadRequest());
        performJson(post("/api/matches/{id}/messages", matchId), a, Map.of("content", "x".repeat(2001)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void unmatchHidesMatchAndBlocksNewMessages() throws Exception {
        TestUser a = register("A");
        TestUser b = register("B");
        long matchId = createMatch(a, b);

        perform(post("/api/matches/{id}/unmatch", matchId), a).andExpect(status().isNoContent());
        perform(post("/api/matches/{id}/unmatch", matchId), a).andExpect(status().isNoContent());

        perform(get("/api/matches"), b).andExpect(jsonPath("$.length()").value(0));
        performJson(post("/api/matches/{id}/messages", matchId), b, Map.of("content", "hello?"))
                .andExpect(status().isConflict());
        perform(post("/api/matches/{id}/unmatch", 999_999), a).andExpect(status().isNotFound());
    }
}
