package com.geomaster.core;

import com.geomaster.core.auth.SigningKeys;
import com.geomaster.core.auth.TestTokens;
import com.geomaster.core.plans.Plans;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Base for end-to-end core API tests: real Postgres (Testcontainers), MockMvc, and service tokens
 * signed with a test Ed25519 key that replaces the web JWKS.
 */
@Import({TestcontainersConfiguration.class, ApiTestSupport.Keys.class})
@SpringBootTest(properties = {
        "geomaster.auth.issuer=" + TestTokens.ISSUER,
        "geomaster.auth.audience=" + TestTokens.AUDIENCE})
@AutoConfigureMockMvc
public abstract class ApiTestSupport {

    protected static final TestTokens TOKENS = new TestTokens("test-key");

    @TestConfiguration
    public static class Keys {
        @Bean
        @Primary
        SigningKeys testSigningKeys() {
            return TOKENS.keys();
        }
    }

    @Autowired
    protected MockMvc mvc;

    @Autowired
    protected JdbcClient db;

    @Autowired
    protected Plans plans;

    /** A signed-in user as Better Auth would create it, plus their provisioned account. */
    public record Session(UUID userId, UUID accountId, String planId) {
        public String token() {
            return token(accountId, planId);
        }

        public String token(UUID acct, String plan) {
            return "Bearer " + TOKENS.sign("EdDSA", "test-key", TestTokens.claims(Instant.now(), Duration.ofMinutes(2),
                    userId.toString(), acct == null ? null : acct.toString(), plan));
        }
    }

    protected UUID newUser() {
        return db.sql("INSERT INTO app_user (email, name) VALUES (:email, 'Test User') RETURNING id")
                .param("email", UUID.randomUUID() + "@example.com")
                .query(UUID.class)
                .single();
    }

    protected Session signIn() throws Exception {
        UUID user = newUser();
        Session provisioning = new Session(user, null, null);
        String body = mvc.perform(post("/v1/users/me").header("Authorization", provisioning.token()))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        UUID acct = UUID.fromString(body.replaceAll(".*\"account\":\\{\"id\":\"([0-9a-f-]+)\".*", "$1"));
        return new Session(user, acct, "free");
    }

    protected ResultActions createProject(Session s, String json) throws Exception {
        return mvc.perform(post("/v1/projects").header("Authorization", s.token())
                .contentType(MediaType.APPLICATION_JSON).content(json));
    }

    /** The {@code "id"} of a single JSON object response. */
    protected static UUID idOf(ResultActions result) throws Exception {
        return UUID.fromString(result.andReturn().getResponse().getContentAsString()
                .replaceAll(".*\"id\":\"([0-9a-f-]+)\".*", "$1"));
    }
}
