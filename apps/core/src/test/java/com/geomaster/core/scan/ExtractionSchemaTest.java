package com.geomaster.core.scan;

import com.geomaster.core.TestcontainersConfiguration;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Guarantees the V6 schema enforces by itself, and the own-brand backfill (spec: extraction-analysis T1). */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class ExtractionSchemaTest {

    @Autowired
    JdbcClient db;

    @Autowired
    PostgreSQLContainer postgres;

    record Tenant(UUID account, UUID project, UUID unit, UUID call) {}

    Tenant tenant() {
        UUID account = db.sql("INSERT INTO account (name) VALUES ('t') RETURNING id").query(UUID.class).single();
        UUID project = db.sql("INSERT INTO project (account_id, domain, country) VALUES (:a, :d, 'CH') RETURNING id")
                .param("a", account).param("d", UUID.randomUUID() + ".ch").query(UUID.class).single();
        UUID prompt = db.sql("INSERT INTO prompt (account_id, project_id, locale, topic) VALUES (:a, :p, 'de', 'reviews') RETURNING id")
                .param("a", account).param("p", project).query(UUID.class).single();
        db.sql("INSERT INTO prompt_version (prompt_id, version, account_id, text) VALUES (:pr, 1, :a, 'Beste Snowboardschule?')")
                .param("pr", prompt).param("a", account).update();
        UUID scan = db.sql("INSERT INTO scan (account_id, project_id, trigger, samples) VALUES (:a, :p, 'manual', 1) RETURNING id")
                .param("a", account).param("p", project).query(UUID.class).single();
        UUID call = db.sql("""
                INSERT INTO llm_call (cache_key, engine, model, locale, prompt_sha256, answer_text, cost_chf, price_version, latency_ms)
                VALUES (:k, 'openai', 'gpt', 'de', repeat('a', 64), 'Ride Flumserberg ist top.', 0.01, 'p-1', 10) RETURNING id
                """).param("k", UUID.randomUUID().toString()).query(UUID.class).single();
        UUID unit = db.sql("""
                INSERT INTO scan_unit (account_id, scan_id, prompt_id, prompt_version, engine, locale, sample, status, llm_call_id)
                VALUES (:a, :s, :pr, 1, 'openai', 'de', 1, 'done', :c) RETURNING id
                """).param("a", account).param("s", scan).param("pr", prompt).param("c", call).query(UUID.class).single();
        return new Tenant(account, project, unit, call);
    }

    UUID brand(Tenant t, String kind, String name) {
        return db.sql("INSERT INTO brand (account_id, project_id, kind, name) VALUES (:a, :p, :k, :n) RETURNING id")
                .param("a", t.account()).param("p", t.project()).param("k", kind).param("n", name)
                .query(UUID.class).single();
    }

    void alias(Tenant t, UUID brand, String normalized) {
        db.sql("""
                INSERT INTO brand_alias (account_id, project_id, brand_id, kind, value, normalized)
                VALUES (:a, :p, :b, 'name', :v, :v)
                """).param("a", t.account()).param("p", t.project()).param("b", brand).param("v", normalized).update();
    }

    UUID extraction(UUID call, String version) {
        return db.sql("INSERT INTO answer_extraction (llm_call_id, extractor_version) VALUES (:c, :v) RETURNING id")
                .param("c", call).param("v", version).query(UUID.class).single();
    }

    void answerBrand(UUID extraction, int ordinal, String name) {
        db.sql("""
                INSERT INTO answer_brand (extraction_id, ordinal, name, normalized, sentiment, recommended, evidence, position)
                VALUES (:e, :o, :n, regexp_replace(lower(:n), '[^a-z0-9]', '', 'g'), 'positive', true, :n, :o)
                """).param("e", extraction).param("o", ordinal).param("n", name).update();
    }

    int mention(Tenant t, UUID extraction, int ordinal, UUID brand) {
        return db.sql("""
                INSERT INTO scan_mention (account_id, scan_id, scan_unit_id, extraction_id, ordinal, brand_id,
                                          extracted_name, position, sentiment, recommended, resolver_version)
                SELECT :a, u.scan_id, u.id, :e, :o, :b, 'x', :o, 'neutral', false, 'res-1' FROM scan_unit u WHERE u.id = :u
                """).param("a", t.account()).param("e", extraction).param("o", ordinal).param("b", brand)
                .param("u", t.unit()).update();
    }

    @Test
    void oneOwnBrandPerProjectAndOwnBrandIsNeverDeleted() {
        Tenant t = tenant();
        UUID own = brand(t, "own", "Ride");
        assertThrows(DataIntegrityViolationException.class, () -> brand(t, "own", "Other"));
        assertDoesNotThrow(() -> brand(t, "competitor", "Rival"));
        assertThrows(DataIntegrityViolationException.class,
                () -> db.sql("UPDATE brand SET deleted_at = now() WHERE id = :b").param("b", own).update());
    }

    @Test
    void aMatchingKeyBelongsToOneActiveBrandPerProject() {
        Tenant t = tenant();
        UUID a = brand(t, "competitor", "A");
        UUID b = brand(t, "competitor", "B");
        alias(t, a, "rival");
        assertThrows(DataIntegrityViolationException.class, () -> alias(t, b, "rival"));
        db.sql("UPDATE brand_alias SET deleted_at = now() WHERE brand_id = :a").param("a", a).update();
        assertDoesNotThrow(() -> alias(t, b, "rival"));
        assertThrows(DataIntegrityViolationException.class, () -> alias(t, b, "Not Normalized"));
    }

    @Test
    void extractionIsUniquePerCallAndVersion() {
        Tenant t = tenant();
        extraction(t.call(), "ext-1");
        assertThrows(DataIntegrityViolationException.class, () -> extraction(t.call(), "ext-1"));
        assertDoesNotThrow(() -> extraction(t.call(), "ext-2"));
        assertThrows(DataIntegrityViolationException.class, () -> extraction(t.call(), "v1"));
    }

    @Test
    void terminalExtractionsCarryTheirRecord() {
        Tenant t = tenant();
        UUID e = extraction(t.call(), "ext-1");
        assertThrows(DataIntegrityViolationException.class,
                () -> db.sql("UPDATE answer_extraction SET status = 'done' WHERE id = :e").param("e", e).update());
        assertThrows(DataIntegrityViolationException.class,
                () -> db.sql("UPDATE answer_extraction SET status = 'failed', finished_at = now() WHERE id = :e")
                        .param("e", e).update());
        assertDoesNotThrow(() -> db.sql("""
                UPDATE answer_extraction SET status = 'done', model = 'claude-haiku-5-5', price_version = 'x-1',
                       finished_at = now() WHERE id = :e
                """).param("e", e).update());
    }

    @Test
    void sameBrandCountsOncePerAnswerAndMentionsStayTenantSafe() {
        Tenant t = tenant();
        Tenant other = tenant();
        UUID own = brand(t, "own", "Ride");
        UUID foreign = brand(other, "own", "Else");
        UUID e = extraction(t.call(), "ext-1");
        answerBrand(e, 1, "Ride Flumserberg");
        answerBrand(e, 2, "rideflumserberg.ch");
        answerBrand(e, 3, "Untracked One");
        answerBrand(e, 4, "Untracked Two");
        mention(t, e, 1, own);
        assertThrows(DataIntegrityViolationException.class, () -> mention(t, e, 2, own));
        assertDoesNotThrow(() -> mention(t, e, 3, null));
        assertDoesNotThrow(() -> mention(t, e, 4, null));
        assertThrows(DataIntegrityViolationException.class, () -> mention(t, e, 2, foreign));
    }

    @Test
    void deletingTheAccountRemovesItsBrandsAndMentions() {
        Tenant t = tenant();
        UUID own = brand(t, "own", "Ride");
        alias(t, own, "ride");
        UUID e = extraction(t.call(), "ext-1");
        answerBrand(e, 1, "Ride");
        mention(t, e, 1, own);
        db.sql("DELETE FROM account WHERE id = :a").param("a", t.account()).update();
        for (String table : new String[] {"brand", "brand_alias", "scan_mention", "scan_citation"}) {
            int left = db.sql("SELECT count(*) FROM " + table + " WHERE account_id = :a")
                    .param("a", t.account()).query(Integer.class).single();
            assertEquals(0, left, table);
        }
        // The shared extraction outlives the tenant, like the llm_call it belongs to (REQ-6).
        assertEquals(1, db.sql("SELECT count(*) FROM answer_brand WHERE extraction_id = :e")
                .param("e", e).query(Integer.class).single());
    }

    @Test
    void backfillGivesExistingProjectsTheirOwnBrand() {
        String name = "backfill_" + UUID.randomUUID().toString().replace("-", "");
        db.sql("CREATE DATABASE " + name).update();
        var ds = new DriverManagerDataSource(
                postgres.getJdbcUrl().replace("/" + postgres.getDatabaseName(), "/" + name),
                postgres.getUsername(), postgres.getPassword());
        Flyway.configure().dataSource(ds).target("5").load().migrate();
        JdbcClient old = JdbcClient.create(ds);
        UUID account = old.sql("INSERT INTO account (name) VALUES ('t') RETURNING id").query(UUID.class).single();
        old.sql("""
                INSERT INTO project (account_id, domain, country)
                VALUES (:a, 'ride-flumserberg.ch', 'CH'), (:a, 'example.co.uk', 'GB')
                """).param("a", account).update();

        Flyway.configure().dataSource(ds).load().migrate();

        List<Map<String, Object>> rows = old.sql("""
                SELECT b.name, b.kind, a.kind AS alias_kind, a.value, a.normalized
                FROM brand b JOIN brand_alias a ON a.brand_id = b.id ORDER BY a.value
                """).query().listOfRows();
        assertEquals(List.of(
                Map.of("name", "Example", "kind", "own", "alias_kind", "domain", "value", "example.co.uk", "normalized", "exampleco"),
                Map.of("name", "Ride Flumserberg", "kind", "own", "alias_kind", "domain", "value", "ride-flumserberg.ch",
                        "normalized", "rideflumserberg")), rows);
    }
}
