package com.geomaster.core.extraction;

import com.geomaster.core.ApiTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import java.util.UUID;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Mentions API over a real Postgres (extraction-analysis T7, REQ-24). */
class MentionsApiTest extends ApiTestSupport {

    static final String ANSWER = "Ride Flumserberg ist top, Snowboard Heroes auch. Siehe www.ride-flumserberg.ch und tripadvisor.";

    @Autowired
    ScanAnalyser analyser;

    record Seeded(UUID scan, UUID unit) {}

    /** A finished scan with one answered unit and its done extraction, as T5 leaves it. */
    Seeded seed(Session s, UUID project) {
        UUID a = s.accountId();
        UUID prompt = db.sql("INSERT INTO prompt (account_id, project_id, locale, topic) VALUES (:a, :p, 'de', 'reviews') RETURNING id")
                .param("a", a).param("p", project).query(UUID.class).single();
        db.sql("INSERT INTO prompt_version (prompt_id, version, account_id, text) VALUES (:pr, 1, :a, 'Snowboardschule?')")
                .param("pr", prompt).param("a", a).update();
        UUID scan = db.sql("""
                INSERT INTO scan (account_id, project_id, status, trigger, samples, finished_at)
                VALUES (:a, :p, 'completed', 'manual', 1, now()) RETURNING id
                """).param("a", a).param("p", project).query(UUID.class).single();
        UUID call = db.sql("""
                INSERT INTO llm_call (cache_key, engine, model, locale, prompt_sha256, answer_text, cost_chf, price_version, latency_ms)
                VALUES (:k, 'openai', 'fake', 'de', repeat('a', 64), :t, 0.01, 'test', 5) RETURNING id
                """).param("k", UUID.randomUUID().toString()).param("t", ANSWER).query(UUID.class).single();
        db.sql("INSERT INTO llm_call_citation (llm_call_id, position, url, domain) VALUES (:c, 1, 'https://tripadvisor.ch/x', 'tripadvisor.ch')")
                .param("c", call).update();
        UUID unit = db.sql("""
                INSERT INTO scan_unit (account_id, scan_id, prompt_id, prompt_version, engine, locale, sample, status, llm_call_id, cost_chf)
                VALUES (:a, :s, :p, 1, 'openai', 'de', 1, 'done', :c, 0.01) RETURNING id
                """).param("a", a).param("s", scan).param("p", prompt).param("c", call).query(UUID.class).single();
        UUID ext = db.sql("""
                INSERT INTO answer_extraction (llm_call_id, extractor_version, status, model, price_version, finished_at, cost_chf)
                VALUES (:c, 'ext-1', 'done', 'claude-haiku-5-5', 'test', now(), 0.0002) RETURNING id
                """).param("c", call).query(UUID.class).single();
        brandRow(ext, 1, "Ride Flumserberg", "rideflumserberg", "positive", true, "Ride Flumserberg ist top", 1);
        brandRow(ext, 2, "Snowboard Heroes", "snowboardheroes", "positive", false, "Snowboard Heroes auch", 2);
        return new Seeded(scan, unit);
    }

    void brandRow(UUID ext, int ordinal, String name, String key, String sentiment, boolean rec, String evidence, int pos) {
        db.sql("""
                INSERT INTO answer_brand (extraction_id, ordinal, name, normalized, sentiment, recommended, evidence, position)
                VALUES (:e, :o, :n, :k, :s, :r, :ev, :p)
                """).param("e", ext).param("o", ordinal).param("n", name).param("k", key).param("s", sentiment)
                .param("r", rec).param("ev", evidence).param("p", pos).update();
    }

    @Test
    void returnsResolvedUntrackedAndCitationsPerAnswerAfterAnalysis() throws Exception {
        Session s = signIn();
        UUID project = idOf(createProject(s, "{\"domain\":\"ride-flumserberg.ch\",\"languages\":[\"de\"],\"country\":\"CH\"}")
                .andExpect(status().isCreated()));
        Seeded seeded = seed(s, project);
        String url = "/v1/scans/" + seeded.scan() + "/mentions";

        mvc.perform(get(url).header("Authorization", s.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.analysedAt").value(nullValue()))
                .andExpect(jsonPath("$.answers", hasSize(1)))
                .andExpect(jsonPath("$.answers[0].extraction").value(nullValue()))
                .andExpect(jsonPath("$.answers[0].mentions", hasSize(0)));

        while (analyser.analyseReady(new ExtractorVersion("ext-1", "test"), 50) > 0) { }

        mvc.perform(get(url).header("Authorization", s.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.extractorVersion").value("ext-1"))
                .andExpect(jsonPath("$.resolverVersion").value("res-1"))
                .andExpect(jsonPath("$.answersExtracted").value(1))
                .andExpect(jsonPath("$.answers[0].unitId").value(seeded.unit().toString()))
                .andExpect(jsonPath("$.answers[0].engine").value("openai"))
                .andExpect(jsonPath("$.answers[0].extraction").value("done"))
                .andExpect(jsonPath("$.answers[0].mentions", hasSize(1)))
                .andExpect(jsonPath("$.answers[0].mentions[0].brandKind").value("own"))
                .andExpect(jsonPath("$.answers[0].mentions[0].brandName").value("Ride Flumserberg"))
                .andExpect(jsonPath("$.answers[0].mentions[0].position").value(1))
                .andExpect(jsonPath("$.answers[0].mentions[0].recommended").value(true))
                .andExpect(jsonPath("$.answers[0].mentions[0].evidence").value("Ride Flumserberg ist top"))
                .andExpect(jsonPath("$.answers[0].untracked", hasSize(1)))
                .andExpect(jsonPath("$.answers[0].untracked[0].name").value("Snowboard Heroes"))
                .andExpect(jsonPath("$.answers[0].untracked[0].position").value(2))
                .andExpect(jsonPath("$.answers[0].citations", hasSize(2)))
                .andExpect(jsonPath("$.answers[0].citations[0].source").value("provider"))
                .andExpect(jsonPath("$.answers[0].citations[0].domainClass").value("third_party"))
                .andExpect(jsonPath("$.answers[0].citations[1].domain").value("ride-flumserberg.ch"))
                .andExpect(jsonPath("$.answers[0].citations[1].domainClass").value("own"));

        // A competitor added later: the next re-resolution turns the untracked brand into a competitor.
        mvc.perform(post("/v1/projects/" + project + "/brands").header("Authorization", s.token())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Snowboard Heroes\"}"))
                .andExpect(status().isCreated());
        while (analyser.reResolveStale(50) > 0) { }
        mvc.perform(get(url).header("Authorization", s.token()))
                .andExpect(jsonPath("$.answers[0].mentions", hasSize(2)))
                .andExpect(jsonPath("$.answers[0].mentions[1].brandKind").value("competitor"))
                .andExpect(jsonPath("$.answers[0].untracked", hasSize(0)));
    }

    @Test
    void otherAccountsGet404() throws Exception {
        Session owner = signIn();
        Session stranger = signIn();
        UUID project = idOf(createProject(owner, "{\"domain\":\"" + UUID.randomUUID() + ".ch\",\"languages\":[\"de\"],\"country\":\"CH\"}")
                .andExpect(status().isCreated()));
        Seeded seeded = seed(owner, project);
        mvc.perform(get("/v1/scans/" + seeded.scan() + "/mentions").header("Authorization", stranger.token()))
                .andExpect(status().isNotFound());
        mvc.perform(get("/v1/scans/" + UUID.randomUUID() + "/mentions").header("Authorization", owner.token()))
                .andExpect(status().isNotFound());
    }
}
