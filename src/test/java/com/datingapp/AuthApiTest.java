package com.datingapp;

import com.datingapp.entity.Gender;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AuthApiTest extends ApiTestSupport {

    @Test
    void registerReturnsTokenAndProfileWithoutPasswordHash() throws Exception {
        Map<String, Object> body = registration("Alice", Gender.FEMALE, Gender.MALE, yearsAgo(30), 40.71, -74.0);

        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.user.name").value("Alice"))
                .andExpect(jsonPath("$.user.age").value(30))
                .andExpect(jsonPath("$.user.createdAt").isString())
                .andExpect(jsonPath("$.user.passwordHash").doesNotExist())
                .andExpect(jsonPath("$.user.password").doesNotExist());
    }

    @Test
    void clientSuppliedIdIsIgnoredSoExistingUsersCannotBeOverwritten() throws Exception {
        TestUser victim = register("Victim");

        Map<String, Object> body = registration("Attacker", Gender.MALE, null, yearsAgo(25), 0, 0);
        body.put("id", victim.id());
        TestUser attacker = register(body);

        assertThat(attacker.id()).isNotEqualTo(victim.id());
        perform(get("/api/users/me"), victim)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Victim"));
    }

    @Test
    void duplicateEmailIsRejectedCaseInsensitively() throws Exception {
        TestUser existing = register("Dup");
        Map<String, Object> body = registration("Dup2", Gender.MALE, null, yearsAgo(25), 0, 0);
        body.put("email", existing.email().toUpperCase());

        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(body)))
                .andExpect(status().isConflict());
    }

    @Test
    void underageRegistrationIsRejected() throws Exception {
        Map<String, Object> body = registration("Teen", Gender.MALE, null, yearsAgo(17), 0, 0);

        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail", containsString("18")));
    }

    @Test
    void invalidRegistrationReturnsFieldErrors() throws Exception {
        Map<String, Object> body = registration("Bad", Gender.MALE, null, yearsAgo(25), 95, 0);
        body.put("email", "not-an-email");
        body.put("password", "short");

        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.email").exists())
                .andExpect(jsonPath("$.errors.password").exists())
                .andExpect(jsonPath("$.errors.latitude").exists());
    }

    @Test
    void invertedAgeRangeIsRejected() throws Exception {
        Map<String, Object> body = registration("Range", Gender.MALE, null, yearsAgo(25), 0, 0);
        body.put("minAge", 40);
        body.put("maxAge", 30);

        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(body)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void loginSucceedsWithCorrectPasswordAndFailsOtherwise() throws Exception {
        TestUser user = register("Login");

        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("email", user.email(), "password", PASSWORD))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty());

        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("email", user.email(), "password", "wrong-password"))))
                .andExpect(status().isUnauthorized());

        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("email", "nobody@example.com", "password", PASSWORD))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void protectedEndpointsRequireAValidToken() throws Exception {
        mvc.perform(get("/api/users/me")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/users/me").header(HttpHeaders.AUTHORIZATION, "Bearer not-a-jwt"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void listAllUsersEndpointNoLongerExists() throws Exception {
        TestUser user = register("Lister");
        perform(get("/api/users"), user).andExpect(status().is4xxClientError());
    }
}
