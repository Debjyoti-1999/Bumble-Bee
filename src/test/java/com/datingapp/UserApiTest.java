package com.datingapp;

import com.datingapp.entity.Gender;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class UserApiTest extends ApiTestSupport {

    @Test
    void partialUpdateChangesOnlyProvidedFields() throws Exception {
        TestUser user = register("Updater");

        performJson(put("/api/users/me"), user, Map.of("bio", "Updated bio", "interests", List.of(" Hiking ", "coffee")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bio").value("Updated bio"))
                .andExpect(jsonPath("$.name").value("Updater"))
                .andExpect(jsonPath("$.interests[0]").value("coffee"))
                .andExpect(jsonPath("$.interests[1]").value("hiking"));
    }

    @Test
    void updateIsValidated() throws Exception {
        TestUser user = register("Validated");

        performJson(put("/api/users/me"), user, Map.of("minAge", 10)).andExpect(status().isBadRequest());
        performJson(put("/api/users/me"), user, Map.of("minAge", 50, "maxAge", 40)).andExpect(status().isBadRequest());
        performJson(put("/api/users/me"), user, Map.of("profilePictureUrl", "javascript:alert(1)"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void publicProfileHidesPrivateFields() throws Exception {
        TestUser viewer = register("Viewer");
        TestUser subject = register("Subject");

        perform(get("/api/users/{id}", subject.id()), viewer)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Subject"))
                .andExpect(jsonPath("$.distanceKm").isNumber())
                .andExpect(jsonPath("$.latitude").doesNotExist())
                .andExpect(jsonPath("$.longitude").doesNotExist())
                .andExpect(jsonPath("$.email").doesNotExist())
                .andExpect(jsonPath("$.dateOfBirth").doesNotExist());

        perform(get("/api/users/{id}", 999_999), viewer).andExpect(status().isNotFound());
    }

    @Test
    void potentialMatchesHonourMutualPreferencesDistanceAndPriorSwipes() throws Exception {
        double[] loc = uniqueLocation();
        double lat = loc[0], lon = loc[1];

        // Seeker: woman looking for men, default 18-60, 50 km.
        TestUser seeker = register("Seeker", Gender.FEMALE, Gender.MALE, lat, lon);

        TestUser nearbyMatch = register("NearbyMatch", Gender.MALE, Gender.FEMALE, lat + 0.1, lon); // ~11 km
        TestUser noPreference = register("NoPref", Gender.MALE, null, lat, lon + 0.1);
        TestUser wrongGender = register("WrongGender", Gender.FEMALE, Gender.FEMALE, lat, lon);
        TestUser wantsSomeoneElse = register("WantsMen", Gender.MALE, Gender.MALE, lat, lon);
        TestUser tooFar = register("TooFar", Gender.MALE, Gender.FEMALE, lat + 1.0, lon); // ~111 km
        TestUser tooOld = register(registration("TooOld", Gender.MALE, Gender.FEMALE, yearsAgo(70), lat, lon));

        Map<String, Object> picky = registration("Picky", Gender.MALE, Gender.FEMALE, yearsAgo(30), lat, lon);
        picky.put("minAge", 40); // seeker is 30
        TestUser seekerTooYoungForThem = register(picky);

        Map<String, Object> shortRange = registration("ShortRange", Gender.MALE, Gender.FEMALE, yearsAgo(30), lat + 0.2, lon);
        shortRange.put("maxDistanceKm", 5); // ~22 km away, outside their radius
        TestUser outsideTheirRadius = register(shortRange);

        TestUser alreadySwiped = register("AlreadySwiped", Gender.MALE, Gender.FEMALE, lat, lon);
        swipe(seeker, alreadySwiped, false);

        String body = perform(get("/api/users/me/potential-matches"), seeker)
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode results = json.readTree(body);

        List<Long> ids = new ArrayList<>();
        results.forEach(n -> ids.add(n.get("id").asLong()));
        assertThat(ids).containsExactlyInAnyOrder(nearbyMatch.id(), noPreference.id());
        assertThat(ids).doesNotContain(seeker.id(), wrongGender.id(), wantsSomeoneElse.id(), tooFar.id(),
                tooOld.id(), seekerTooYoungForThem.id(), outsideTheirRadius.id(), alreadySwiped.id());

        JsonNode first = results.get(0);
        assertThat(first.has("latitude")).isFalse();
        assertThat(first.get("distanceKm").asInt()).isBetween(1, 50);
    }

    @Test
    void potentialMatchesArePaginated() throws Exception {
        double[] loc = uniqueLocation();
        TestUser seeker = register("PagedSeeker", Gender.FEMALE, null, loc[0], loc[1]);
        for (int i = 0; i < 3; i++) {
            register("Candidate" + i, Gender.MALE, null, loc[0], loc[1]);
        }

        perform(get("/api/users/me/potential-matches").param("size", "2"), seeker)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
        perform(get("/api/users/me/potential-matches").param("size", "2").param("page", "1"), seeker)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
        perform(get("/api/users/me/potential-matches").param("size", "500"), seeker)
                .andExpect(status().isBadRequest());
    }

    @Test
    void deletingAccountRemovesItsSwipesMatchesAndMessages() throws Exception {
        TestUser leaver = register("Leaver");
        TestUser partner = register("Partner");
        long matchId = createMatch(leaver, partner);
        sendMessage(leaver, matchId, "bye");

        perform(delete("/api/users/me"), leaver).andExpect(status().isNoContent());

        perform(get("/api/matches"), partner)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
        perform(get("/api/matches/{id}/messages", matchId), partner).andExpect(status().isNotFound());
        perform(get("/api/users/{id}", leaver.id()), partner).andExpect(status().isNotFound());
        // The leaver's still-unexpired token no longer resolves to an account.
        perform(get("/api/users/me"), leaver).andExpect(status().isNotFound());
    }
}
