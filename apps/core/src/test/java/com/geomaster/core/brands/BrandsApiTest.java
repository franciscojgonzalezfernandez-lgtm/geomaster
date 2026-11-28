package com.geomaster.core.brands;

import com.geomaster.core.ApiTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import java.util.UUID;

import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Brands API over a real Postgres (extraction-analysis T2, T6). */
class BrandsApiTest extends ApiTestSupport {

    UUID project(Session s, String domain) throws Exception {
        return idOf(createProject(s, "{\"domain\":\"" + domain + "\",\"languages\":[\"de\"],\"country\":\"CH\"}")
                .andExpect(status().isCreated()));
    }

    String base(UUID project) {
        return "/v1/projects/" + project + "/brands";
    }

    ResultActions addCompetitor(Session s, UUID project, String json) throws Exception {
        return mvc.perform(post(base(project)).header("Authorization", s.token())
                .contentType(MediaType.APPLICATION_JSON).content(json));
    }

    /** The top-level {@code "id"} of a brand response (aliases carry ids too). */
    static UUID brandId(ResultActions result) throws Exception {
        return UUID.fromString(result.andReturn().getResponse().getContentAsString()
                .replaceFirst("^\\{\"id\":\"([0-9a-f-]+)\".*", "$1"));
    }

    /** Move the session's account to {@code plan} (more domains for multi-project tests). */
    Session onPlan(Session s, String plan) {
        db.sql("UPDATE account SET plan_id = :p WHERE id = :a").param("p", plan).param("a", s.accountId()).update();
        return new Session(s.userId(), s.accountId(), plan);
    }

    UUID ownId(Session s, UUID project) throws Exception {
        String body = mvc.perform(get(base(project)).header("Authorization", s.token()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return UUID.fromString(body.replaceFirst("^\\{\"own\":\\{\"id\":\"([0-9a-f-]+)\".*", "$1"));
    }

    @Test
    void newProjectGetsItsOwnBrandFromTheDomain() throws Exception {
        Session s = signIn();
        UUID project = project(s, "https://www.Ride-" + UUID.randomUUID().toString().substring(0, 8) + ".ch/");
        mvc.perform(get(base(project)).header("Authorization", s.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.own.kind").value("own"))
                .andExpect(jsonPath("$.own.name").value(org.hamcrest.Matchers.startsWith("Ride ")))
                .andExpect(jsonPath("$.own.aliases", hasSize(1)))
                .andExpect(jsonPath("$.own.aliases[0].kind").value("domain"))
                .andExpect(jsonPath("$.competitors", hasSize(0)));
    }

    @Test
    void competitorsWithAliasesRenameAndDelete() throws Exception {
        Session s = signIn();
        UUID project = project(s, UUID.randomUUID() + ".ch");
        UUID rival = brandId(addCompetitor(s, project,
                "{\"name\":\"Skischule Flumserberg\",\"domain\":\"skischule-flumserberg.ch\",\"aliases\":[\"SSF\",\"ssf\"]}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.kind").value("competitor"))
                .andExpect(jsonPath("$.aliases", hasSize(2)))); // domain + SSF (ssf is the same key)

        mvc.perform(patch(base(project) + "/" + rival).header("Authorization", s.token())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Schneesportschule Flumserberg\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.name").value("Schneesportschule Flumserberg"));

        String body = mvc.perform(post(base(project) + "/" + rival + "/aliases").header("Authorization", s.token())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"kind\":\"name\",\"value\":\"Skischule Flums\"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.aliases", hasSize(3)))
                .andReturn().getResponse().getContentAsString();
        UUID alias = UUID.fromString(body.replaceAll(".*\"id\":\"([0-9a-f-]+)\",\"kind\":\"name\",\"value\":\"Skischule Flums\".*", "$1"));
        mvc.perform(delete(base(project) + "/" + rival + "/aliases/" + alias).header("Authorization", s.token()))
                .andExpect(status().isNoContent());
        mvc.perform(delete(base(project) + "/" + rival + "/aliases/" + alias).header("Authorization", s.token()))
                .andExpect(status().isNotFound());

        mvc.perform(delete(base(project) + "/" + rival).header("Authorization", s.token()))
                .andExpect(status().isNoContent());
        mvc.perform(get(base(project)).header("Authorization", s.token()))
                .andExpect(jsonPath("$.competitors", hasSize(0)));
        // Soft delete: the row stays for past mentions (REQ-3), and its keys are free again.
        assertEquals(1, db.sql("SELECT count(*) FROM brand WHERE id = :id AND deleted_at IS NOT NULL")
                .param("id", rival).query(Integer.class).single());
        addCompetitor(s, project, "{\"name\":\"Skischule Flumserberg AG\"}").andExpect(status().isCreated());
        // Six successful edits, each queues re-resolution of the project's scans (REQ-15); the 404 is not one.
        assertEquals(6, db.sql("SELECT brands_version FROM project WHERE id = :id")
                .param("id", project).query(Integer.class).single());
    }

    @Test
    void rejectsNamesAndDomainsThatMatchAnExistingBrand() throws Exception {
        Session s = signIn();
        String stem = "ride" + UUID.randomUUID().toString().substring(0, 8);
        UUID project = project(s, stem + ".ch");
        // Same key as the own brand's domain alias.
        addCompetitor(s, project, "{\"name\":\"" + stem.toUpperCase() + " GmbH\"}")
                .andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.field").value("name"));
        addCompetitor(s, project, "{\"name\":\"Rival\",\"domain\":\"" + stem + ".de\"}")
                .andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.field").value("domain"));
        addCompetitor(s, project, "{\"name\":\"Rival\"}").andExpect(status().isCreated());
        addCompetitor(s, project, "{\"name\":\"rival ag\"}")
                .andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.field").value("name"));
        UUID own = ownId(s, project);
        mvc.perform(patch(base(project) + "/" + own).header("Authorization", s.token())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Rival\"}"))
                .andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.field").value("name"));
        mvc.perform(post(base(project) + "/" + own + "/aliases").header("Authorization", s.token())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"kind\":\"domain\",\"value\":\"rival.com\"}"))
                .andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.field").value("value"));
        mvc.perform(delete(base(project) + "/" + own).header("Authorization", s.token()))
                .andExpect(status().isUnprocessableContent());
        addCompetitor(s, project, "{\"name\":\"!!!\"}")
                .andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.field").value("name"));
    }

    @Test
    void competitorLimitComesFromPlansJson() throws Exception {
        Session s = signIn();
        UUID project = project(s, UUID.randomUUID() + ".ch");
        int max = plans.get("free").competitorsPerDomain();
        for (int i = 0; i < max; i++) {
            addCompetitor(s, project, "{\"name\":\"Rival " + (char) ('a' + i) + "\"}").andExpect(status().isCreated());
        }
        addCompetitor(s, project, "{\"name\":\"One Too Many\"}")
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.limit").value("competitorsPerDomain"))
                .andExpect(jsonPath("$.allowed").value(max));
        // The limit is per domain: another project starts from zero.
        Session pro = onPlan(s, "pro");
        UUID second = project(pro, UUID.randomUUID() + ".ch");
        addCompetitor(pro, second, "{\"name\":\"Rival a\"}").andExpect(status().isCreated());
    }

    @Test
    void otherAccountsSeeNothing() throws Exception {
        Session owner = onPlan(signIn(), "pro");
        Session stranger = signIn();
        UUID project = project(owner, UUID.randomUUID() + ".ch");
        UUID rival = brandId(addCompetitor(owner, project, "{\"name\":\"Rival\"}").andExpect(status().isCreated()));
        mvc.perform(get(base(project)).header("Authorization", stranger.token())).andExpect(status().isNotFound());
        addCompetitor(stranger, project, "{\"name\":\"X\"}").andExpect(status().isNotFound());
        mvc.perform(delete(base(project) + "/" + rival).header("Authorization", stranger.token()))
                .andExpect(status().isNotFound());
        // A brand id of another project of the same account is not found under this project.
        UUID other = project(owner, UUID.randomUUID() + ".ch");
        mvc.perform(delete(base(other) + "/" + rival).header("Authorization", owner.token()))
                .andExpect(status().isNotFound());
    }
}
