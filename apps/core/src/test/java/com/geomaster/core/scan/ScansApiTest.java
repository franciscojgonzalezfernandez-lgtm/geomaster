package com.geomaster.core.scan;

import com.geomaster.core.ApiTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.ResultActions;

import java.util.UUID;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Scans API over a real Postgres (scan-engine T6, REQ-16, REQ-17). Workers are off: scans stay queued. */
@TestPropertySource(properties = {
        "geomaster.providers.real-adapters=false",
        "geomaster.scan.worker.enabled=false",
        "geomaster.providers.engines.openai.enabled=true",
        "geomaster.providers.engines.openai.model=test-openai"})
class ScansApiTest extends ApiTestSupport {

    UUID projectWithPrompts(Session s, int prompts) throws Exception {
        UUID project = idOf(createProject(s, "{\"domain\":\"" + UUID.randomUUID() + ".ch\",\"languages\":[\"de\"],\"country\":\"CH\"}")
                .andExpect(status().isCreated()));
        for (int i = 0; i < prompts; i++) {
            mvc.perform(post("/v1/projects/" + project + "/prompts").header("Authorization", s.token())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"locale\":\"de\",\"topic\":\"reviews\",\"text\":\"Frage " + i + "\"}"))
                    .andExpect(status().isCreated());
        }
        return project;
    }

    ResultActions start(Session s, UUID project) throws Exception {
        return mvc.perform(post("/v1/projects/" + project + "/scans").header("Authorization", s.token()));
    }

    @Test
    void startReturnsTheQueuedScanWithPerEngineProgress() throws Exception {
        Session s = signIn();
        UUID project = projectWithPrompts(s, 3);
        UUID scan = idOf(start(s, project)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("queued"))
                .andExpect(jsonPath("$.trigger").value("manual"))
                .andExpect(jsonPath("$.samples").value(2))
                .andExpect(jsonPath("$.unitsTotal").value(6))
                .andExpect(jsonPath("$.unitsDone").value(0))
                .andExpect(jsonPath("$.costChf").value(0))
                .andExpect(jsonPath("$.engines", hasSize(1)))
                .andExpect(jsonPath("$.engines[0].engine").value("openai"))
                .andExpect(jsonPath("$.engines[0].model").value("test-openai"))
                .andExpect(jsonPath("$.engines[0].total").value(6)));

        // A failed unit shows up in the detail (REQ-17).
        db.sql("UPDATE scan_unit SET status = 'failed', last_error = 'x' WHERE id = (SELECT id FROM scan_unit WHERE scan_id = :s LIMIT 1)")
                .param("s", scan).update();
        db.sql("UPDATE scan SET units_failed = 1 WHERE id = :s").param("s", scan).update();
        mvc.perform(get("/v1/scans/" + scan).header("Authorization", s.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(scan.toString()))
                .andExpect(jsonPath("$.projectId").value(project.toString()))
                .andExpect(jsonPath("$.unitsFailed").value(1))
                .andExpect(jsonPath("$.engines[0].failed").value(1));
    }

    @Test
    void secondStartWhileActiveIs409() throws Exception {
        Session s = signIn();
        UUID project = projectWithPrompts(s, 1);
        start(s, project).andExpect(status().isCreated());
        start(s, project)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.reason").value("already_active"));
    }

    @Test
    void freePlanSecondScanIs409Frequency() throws Exception {
        Session s = signIn();
        UUID project = projectWithPrompts(s, 1);
        UUID scan = idOf(start(s, project).andExpect(status().isCreated()));
        db.sql("UPDATE scan SET status = 'completed', finished_at = now() WHERE id = :s").param("s", scan).update();
        start(s, project)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.reason").value("frequency"));
    }

    @Test
    void projectWithoutPromptsIs409NoPrompts() throws Exception {
        Session s = signIn();
        start(s, projectWithPrompts(s, 0))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.reason").value("no_prompts"));
    }

    @Test
    void listIsNewestFirstAndScoped() throws Exception {
        Session s = signIn();
        UUID project = projectWithPrompts(s, 1);
        UUID first = idOf(start(s, project).andExpect(status().isCreated()));
        db.sql("UPDATE scan SET status = 'failed', finished_at = now() WHERE id = :s").param("s", first).update();
        UUID second = idOf(start(s, project).andExpect(status().isCreated()));

        mvc.perform(get("/v1/projects/" + project + "/scans").header("Authorization", s.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].id").value(second.toString()))
                .andExpect(jsonPath("$[1].id").value(first.toString()))
                .andExpect(jsonPath("$[1].status").value("failed"));
    }

    @Test
    void otherAccountsGet404() throws Exception {
        Session owner = signIn();
        UUID project = projectWithPrompts(owner, 1);
        UUID scan = idOf(start(owner, project).andExpect(status().isCreated()));

        Session other = signIn();
        mvc.perform(get("/v1/scans/" + scan).header("Authorization", other.token())).andExpect(status().isNotFound());
        mvc.perform(get("/v1/projects/" + project + "/scans").header("Authorization", other.token())).andExpect(status().isNotFound());
        start(other, project).andExpect(status().isNotFound());
        mvc.perform(get("/v1/scans/" + UUID.randomUUID()).header("Authorization", owner.token())).andExpect(status().isNotFound());
    }

    @Test
    void requiresAServiceToken() throws Exception {
        mvc.perform(get("/v1/scans/" + UUID.randomUUID())).andExpect(status().isUnauthorized());
    }
}
