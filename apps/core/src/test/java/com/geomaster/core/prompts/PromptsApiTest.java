package com.geomaster.core.prompts;

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

/** Prompts API over a real Postgres (scan-engine T2). */
class PromptsApiTest extends ApiTestSupport {

    UUID project(Session s) throws Exception {
        return idOf(createProject(s, "{\"domain\":\"" + UUID.randomUUID() + ".ch\",\"languages\":[\"de\"],\"country\":\"CH\"}")
                .andExpect(status().isCreated()));
    }

    ResultActions add(Session s, UUID project, String json) throws Exception {
        return mvc.perform(post("/v1/projects/" + project + "/prompts").header("Authorization", s.token())
                .contentType(MediaType.APPLICATION_JSON).content(json));
    }

    @Test
    void addListEditDelete() throws Exception {
        Session s = signIn();
        UUID project = project(s);
        UUID prompt = idOf(add(s, project, "{\"locale\":\"de\",\"topic\":\"reviews\",\"text\":\"  Beste Snowboardschule Flumserberg?  \"}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.text").value("Beste Snowboardschule Flumserberg?"))
                .andExpect(jsonPath("$.version").value(1)));

        mvc.perform(patch("/v1/projects/" + project + "/prompts/" + prompt).header("Authorization", s.token())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"text\":\"Snowboardkurs Flumserberg Erfahrungen?\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(2));
        // Old version is kept for answers asked with it (REQ-2).
        int versions = db.sql("SELECT count(*) FROM prompt_version WHERE prompt_id = :p").param("p", prompt).query(Integer.class).single();
        assertEquals(2, versions);

        mvc.perform(get("/v1/projects/" + project + "/prompts").header("Authorization", s.token()))
                .andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(1)));
        mvc.perform(delete("/v1/projects/" + project + "/prompts/" + prompt).header("Authorization", s.token()))
                .andExpect(status().isNoContent());
        mvc.perform(get("/v1/projects/" + project + "/prompts").header("Authorization", s.token()))
                .andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void validatesLocaleTopicAndText() throws Exception {
        Session s = signIn();
        UUID project = project(s);
        add(s, project, "{\"locale\":\"fr\",\"topic\":\"reviews\",\"text\":\"x\"}") // project measures only de
                .andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.field").value("locale"));
        add(s, project, "{\"locale\":\"de\",\"topic\":\"gossip\",\"text\":\"x\"}")
                .andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.field").value("topic"));
        add(s, project, "{\"locale\":\"de\",\"topic\":\"reviews\",\"text\":\"   \"}")
                .andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.field").value("text"));
    }

    @Test
    void promptsLimitComesFromPlansJsonAndCountsActiveOnly() throws Exception {
        Session s = signIn();
        UUID project = project(s);
        int max = plans.get("free").prompts();
        UUID first = null;
        for (int i = 0; i < max; i++) {
            UUID id = idOf(add(s, project, "{\"locale\":\"de\",\"topic\":\"how_to\",\"text\":\"Frage " + i + "\"}")
                    .andExpect(status().isCreated()));
            if (first == null) {
                first = id;
            }
        }
        add(s, project, "{\"locale\":\"de\",\"topic\":\"how_to\",\"text\":\"one too many\"}")
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.limit").value("prompts"))
                .andExpect(jsonPath("$.allowed").value(max));
        // Deleting frees a slot.
        mvc.perform(delete("/v1/projects/" + project + "/prompts/" + first).header("Authorization", s.token()))
                .andExpect(status().isNoContent());
        add(s, project, "{\"locale\":\"de\",\"topic\":\"how_to\",\"text\":\"now it fits\"}").andExpect(status().isCreated());
    }

    @Test
    void anotherAccountsProjectOrPromptIs404() throws Exception {
        Session owner = signIn();
        Session other = signIn();
        UUID project = project(owner);
        UUID prompt = idOf(add(owner, project, "{\"locale\":\"de\",\"topic\":\"pricing\",\"text\":\"Preise?\"}")
                .andExpect(status().isCreated()));

        mvc.perform(get("/v1/projects/" + project + "/prompts").header("Authorization", other.token()))
                .andExpect(status().isNotFound());
        add(other, project, "{\"locale\":\"de\",\"topic\":\"pricing\",\"text\":\"x\"}").andExpect(status().isNotFound());
        mvc.perform(delete("/v1/projects/" + project + "/prompts/" + prompt).header("Authorization", other.token()))
                .andExpect(status().isNotFound());
    }
}
