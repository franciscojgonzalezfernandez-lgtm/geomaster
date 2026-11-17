package com.geomaster.core.accounts;

import com.geomaster.core.TestcontainersConfiguration;
import com.geomaster.core.auth.SigningKeys;
import com.geomaster.core.auth.TestTokens;
import com.geomaster.core.plans.Plans;
import org.junit.jupiter.api.Test;
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
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** End-to-end core API over a real Postgres (Testcontainers), signed with a test Ed25519 key. */
@Import({TestcontainersConfiguration.class, AccountsApiTest.Keys.class})
@SpringBootTest(properties = {
        "geomaster.auth.issuer=" + TestTokens.ISSUER,
        "geomaster.auth.audience=" + TestTokens.AUDIENCE})
@AutoConfigureMockMvc
class AccountsApiTest {

    static final TestTokens TOKENS = new TestTokens("test-key");

    @TestConfiguration
    static class Keys {
        @Bean
        @Primary
        SigningKeys testSigningKeys() {
            return TOKENS.keys();
        }
    }

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcClient db;

    @Autowired
    Plans plans;

    /** A signed-in user as Better Auth would create it, plus their provisioned account. */
    record Session(UUID userId, UUID accountId, String planId) {
        String token() {
            return token(accountId, planId);
        }

        String token(UUID acct, String plan) {
            return "Bearer " + TOKENS.sign("EdDSA", "test-key", TestTokens.claims(Instant.now(), Duration.ofMinutes(2),
                    userId.toString(), acct == null ? null : acct.toString(), plan));
        }
    }

    UUID newUser() {
        return db.sql("INSERT INTO app_user (email, name) VALUES (:email, 'Test User') RETURNING id")
                .param("email", UUID.randomUUID() + "@example.com")
                .query(UUID.class)
                .single();
    }

    Session signIn() throws Exception {
        UUID user = newUser();
        Session provisioning = new Session(user, null, null);
        String body = mvc.perform(post("/v1/users/me").header("Authorization", provisioning.token()))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        UUID acct = UUID.fromString(body.replaceAll(".*\"account\":\\{\"id\":\"([0-9a-f-]+)\".*", "$1"));
        return new Session(user, acct, "free");
    }

    ResultActions createProject(Session s, String json) throws Exception {
        return mvc.perform(post("/v1/projects").header("Authorization", s.token())
                .contentType(MediaType.APPLICATION_JSON).content(json));
    }

    @Test
    void firstSignInProvisionsOneFreeAccountAndReturningUserReusesIt() throws Exception {
        UUID user = newUser();
        Session provisioning = new Session(user, null, null);

        mvc.perform(post("/v1/users/me").header("Authorization", provisioning.token()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.account.planId").value("free"))
                .andExpect(jsonPath("$.user.interfaceLocale").value("en"));
        mvc.perform(post("/v1/users/me").header("Authorization", provisioning.token()))
                .andExpect(status().isOk());

        int accounts = db.sql("SELECT count(*) FROM account_member WHERE user_id = :u AND role = 'owner'")
                .param("u", user).query(Integer.class).single();
        assertEquals(1, accounts, "returning user must not get a second account (REQ-3)");
    }

    @Test
    void accountLessTokenCannotReadBusinessData() throws Exception {
        Session provisioning = new Session(newUser(), null, null);
        mvc.perform(get("/v1/projects").header("Authorization", provisioning.token()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void missingOrInvalidTokenIs401() throws Exception {
        mvc.perform(get("/v1/projects")).andExpect(status().isUnauthorized());
        mvc.perform(get("/v1/account").header("Authorization", "Bearer nope")).andExpect(status().isUnauthorized());
    }

    @Test
    void interfaceLocaleIsPersisted() throws Exception {
        Session s = signIn();
        mvc.perform(patch("/v1/users/me").header("Authorization", s.token())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"interfaceLocale\":\"de\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.interfaceLocale").value("de"));
        mvc.perform(patch("/v1/users/me").header("Authorization", s.token())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"interfaceLocale\":\"it\"}"))
                .andExpect(status().isUnprocessableContent());
    }

    @Test
    void getAccountReturnsCurrentAccountAndRejectsForeignAccountClaim() throws Exception {
        Session s = signIn();
        Session other = signIn();
        mvc.perform(get("/v1/account").header("Authorization", s.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(s.accountId().toString()));
        // A token claiming an account the user is not a member of sees nothing.
        mvc.perform(get("/v1/account").header("Authorization", s.token(other.accountId(), "free")))
                .andExpect(status().isNotFound());
    }

    @Test
    void createNormalisesDomainAndIsIdempotent() throws Exception {
        Session s = signIn();
        String id = createProject(s, "{\"domain\":\"https://www.RideFlumserberg.ch/\",\"languages\":[\"de\"],\"country\":\"ch\",\"region\":\"SG\"}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.domain").value("rideflumserberg.ch"))
                .andExpect(jsonPath("$.country").value("CH"))
                .andExpect(jsonPath("$.languages[0]").value("de"))
                .andReturn().getResponse().getContentAsString().replaceAll(".*\"id\":\"([0-9a-f-]+)\".*", "$1");

        createProject(s, "{\"domain\":\"rideflumserberg.ch\",\"languages\":[\"de\"],\"country\":\"CH\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id));
        mvc.perform(get("/v1/projects").header("Authorization", s.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)));
    }

    @Test
    void invalidInputIs422NamingTheField() throws Exception {
        Session s = signIn();
        createProject(s, "{\"domain\":\"not a domain\",\"languages\":[\"de\"],\"country\":\"CH\"}")
                .andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.field").value("domain"));
        createProject(s, "{\"domain\":\"a.ch\",\"languages\":[],\"country\":\"CH\"}")
                .andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.field").value("languages"));
        createProject(s, "{\"domain\":\"a.ch\",\"languages\":[\"it\"],\"country\":\"CH\"}")
                .andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.field").value("languages"));
        createProject(s, "{\"domain\":\"a.ch\",\"languages\":[\"de\"],\"country\":\"Switzerland\"}")
                .andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.field").value("country"));
    }

    @Test
    void planLimitsComeFromPlansJson() throws Exception {
        Session s = signIn();
        int maxLanguages = plans.get("free").languages();
        int maxDomains = plans.get("free").domains();

        List<String> tooMany = List.of("en", "de", "fr", "es").subList(0, Math.min(4, maxLanguages + 1));
        if (tooMany.size() > maxLanguages) {
            createProject(s, "{\"domain\":\"langs.ch\",\"languages\":" + json(tooMany) + ",\"country\":\"CH\"}")
                    .andExpect(status().isUnprocessableContent())
                    .andExpect(jsonPath("$.limit").value("languages"))
                    .andExpect(jsonPath("$.allowed").value(maxLanguages));
        }

        for (int i = 0; i < maxDomains; i++) {
            createProject(s, "{\"domain\":\"site" + i + ".ch\",\"languages\":[\"de\"],\"country\":\"CH\"}")
                    .andExpect(status().isCreated());
        }
        createProject(s, "{\"domain\":\"one-too-many.ch\",\"languages\":[\"de\"],\"country\":\"CH\"}")
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.limit").value("domains"))
                .andExpect(jsonPath("$.allowed").value(maxDomains));
        // Re-creating an existing domain is idempotent, not a limit breach (REQ-10).
        createProject(s, "{\"domain\":\"site0.ch\",\"languages\":[\"de\"],\"country\":\"CH\"}")
                .andExpect(status().isOk());
    }

    @Test
    void anotherAccountsProjectIs404() throws Exception {
        Session owner = signIn();
        Session other = signIn();
        String id = createProject(owner, "{\"domain\":\"owner.ch\",\"languages\":[\"de\"],\"country\":\"CH\"}")
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString().replaceAll(".*\"id\":\"([0-9a-f-]+)\".*", "$1");

        mvc.perform(get("/v1/projects/" + id).header("Authorization", owner.token())).andExpect(status().isOk());
        mvc.perform(get("/v1/projects/" + id).header("Authorization", other.token())).andExpect(status().isNotFound());
        mvc.perform(get("/v1/projects").header("Authorization", other.token()))
                .andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void projectReadsRequireMembershipOfTheTokensAccount() throws Exception {
        Session owner = signIn();
        Session intruder = signIn();
        createProject(owner, "{\"domain\":\"members-only.ch\",\"languages\":[\"de\"],\"country\":\"CH\"}")
                .andExpect(status().isCreated());
        mvc.perform(get("/v1/projects").header("Authorization", intruder.token(owner.accountId(), "free")))
                .andExpect(status().isNotFound());
    }

    @Test
    void ownerDeletesAccountAndEveryTenantRowGoes() throws Exception {
        Session s = signIn();
        String id = createProject(s, "{\"domain\":\"delete-me.ch\",\"languages\":[\"de\"],\"country\":\"CH\"}")
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString().replaceAll(".*\"id\":\"([0-9a-f-]+)\".*", "$1");

        mvc.perform(delete("/v1/account").header("Authorization", s.token())).andExpect(status().isNoContent());

        // Every table with an account_id column (now and in future migrations) must be empty for it.
        List<String> tables = db.sql("""
                SELECT table_name FROM information_schema.columns
                WHERE table_schema = 'public' AND column_name = 'account_id'
                """).query(String.class).list();
        for (String table : tables) {
            int left = db.sql("SELECT count(*) FROM " + table + " WHERE account_id = :a")
                    .param("a", s.accountId()).query(Integer.class).single();
            assertEquals(0, left, table + " still holds rows for the deleted account (REQ-18)");
        }
        int languages = db.sql("SELECT count(*) FROM project_language WHERE project_id = :p")
                .param("p", UUID.fromString(id)).query(Integer.class).single();
        assertEquals(0, languages);
        mvc.perform(get("/v1/account").header("Authorization", s.token())).andExpect(status().isNotFound());
    }

    @Test
    void onlyAnOwnerCanDeleteTheAccount() throws Exception {
        Session owner = signIn();
        UUID member = newUser();
        db.sql("INSERT INTO account_member (account_id, user_id, role) VALUES (:a, :u, 'member')")
                .param("a", owner.accountId()).param("u", member).update();
        Session asMember = new Session(member, owner.accountId(), "free");

        mvc.perform(delete("/v1/account").header("Authorization", asMember.token())).andExpect(status().isForbidden());
        mvc.perform(get("/v1/account").header("Authorization", owner.token())).andExpect(status().isOk());
    }

    private static String json(List<String> values) {
        List<String> quoted = new ArrayList<>();
        values.forEach(v -> quoted.add("\"" + v + "\""));
        return "[" + String.join(",", quoted) + "]";
    }
}
