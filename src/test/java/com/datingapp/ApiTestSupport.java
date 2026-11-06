package com.datingapp;

import com.datingapp.entity.Gender;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Shared helpers for API tests. All tests share one in-memory database, so every test uses unique
 * emails and, where location matters, its own geographic area (see {@link #uniqueLocation()}).
 */
@SpringBootTest
@AutoConfigureMockMvc
public abstract class ApiTestSupport {

    protected static final String PASSWORD = "correct-horse-battery";
    private static final AtomicInteger AREA_SEQ = new AtomicInteger();

    @Autowired
    protected MockMvc mvc;

    @Autowired
    protected JsonMapper json;

    public record TestUser(long id, String token, String email) {
    }

    /** A point at least ~200 km from any other test's point. */
    protected static double[] uniqueLocation() {
        int n = AREA_SEQ.getAndIncrement();
        return new double[]{-50 + (n % 50) * 2.0, -150 + (n / 50) * 5.0};
    }

    protected static LocalDate yearsAgo(int years) {
        return LocalDate.now(ZoneOffset.UTC).minusYears(years);
    }

    protected Map<String, Object> registration(String name, Gender gender, Gender preferredGender,
                                               LocalDate dob, double lat, double lon) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("email", name.toLowerCase() + "-" + UUID.randomUUID() + "@example.com");
        body.put("password", PASSWORD);
        body.put("name", name);
        body.put("dateOfBirth", dob.toString());
        body.put("gender", gender.name());
        body.put("latitude", lat);
        body.put("longitude", lon);
        if (preferredGender != null) {
            body.put("preferredGender", preferredGender.name());
        }
        return body;
    }

    protected TestUser register(Map<String, Object> body) throws Exception {
        String response = mvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        JsonNode node = json.readTree(response);
        return new TestUser(node.get("user").get("id").asLong(), node.get("accessToken").asString(),
                node.get("user").get("email").asString());
    }

    protected TestUser register(String name, Gender gender, Gender preferredGender, double lat, double lon)
            throws Exception {
        return register(registration(name, gender, preferredGender, yearsAgo(30), lat, lon));
    }

    /** A user whose location doesn't matter for the test. */
    protected TestUser register(String name) throws Exception {
        double[] loc = uniqueLocation();
        return register(name, Gender.FEMALE, null, loc[0], loc[1]);
    }

    protected ResultActions perform(MockHttpServletRequestBuilder request, TestUser as) throws Exception {
        return mvc.perform(request.header(HttpHeaders.AUTHORIZATION, "Bearer " + as.token()));
    }

    protected ResultActions performJson(MockHttpServletRequestBuilder request, TestUser as, Object body)
            throws Exception {
        return perform(request.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body)), as);
    }

    protected JsonNode swipe(TestUser swiper, TestUser swiped, boolean liked) throws Exception {
        String response = performJson(post("/api/swipes"), swiper, Map.of("swipedId", swiped.id(), "liked", liked))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(response);
    }

    protected long createMatch(TestUser a, TestUser b) throws Exception {
        swipe(a, b, true);
        return swipe(b, a, true).get("matchId").asLong();
    }

    protected JsonNode sendMessage(TestUser sender, long matchId, String content) throws Exception {
        String response = performJson(post("/api/matches/{id}/messages", matchId), sender, Map.of("content", content))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(response);
    }
}
