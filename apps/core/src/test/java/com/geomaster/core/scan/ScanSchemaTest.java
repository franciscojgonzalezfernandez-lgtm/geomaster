package com.geomaster.core.scan;

import com.geomaster.core.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Guarantees the V3 schema enforces by itself (spec: scan-engine T1). */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class ScanSchemaTest {

    @Autowired
    JdbcClient db;

    record Tenant(UUID account, UUID project, UUID prompt) {}

    Tenant tenant() {
        UUID account = db.sql("INSERT INTO account (name) VALUES ('t') RETURNING id").query(UUID.class).single();
        UUID project = db.sql("INSERT INTO project (account_id, domain, country) VALUES (:a, :d, 'CH') RETURNING id")
                .param("a", account).param("d", UUID.randomUUID() + ".ch").query(UUID.class).single();
        UUID prompt = db.sql("""
                INSERT INTO prompt (account_id, project_id, locale, topic) VALUES (:a, :p, 'de', 'reviews') RETURNING id
                """).param("a", account).param("p", project).query(UUID.class).single();
        db.sql("INSERT INTO prompt_version (prompt_id, version, account_id, text) VALUES (:pr, 1, :a, 'Beste Snowboardschule?')")
                .param("pr", prompt).param("a", account).update();
        return new Tenant(account, project, prompt);
    }

    UUID scan(Tenant t, String status) {
        return db.sql("""
                INSERT INTO scan (account_id, project_id, status, trigger, samples) VALUES (:a, :p, :s, 'manual', 1) RETURNING id
                """).param("a", t.account()).param("p", t.project()).param("s", status).query(UUID.class).single();
    }

    int unit(Tenant t, UUID scan, UUID prompt, int sample) {
        return db.sql("""
                INSERT INTO scan_unit (account_id, scan_id, prompt_id, prompt_version, engine, locale, sample)
                VALUES (:a, :s, :pr, 1, 'openai', 'de', :n)
                """).param("a", t.account()).param("s", scan).param("pr", prompt).param("n", sample).update();
    }

    @Test
    void unitIdempotencyKeyRejectsDuplicates() {
        Tenant t = tenant();
        UUID scan = scan(t, "queued");
        unit(t, scan, t.prompt(), 1);
        assertDoesNotThrow(() -> unit(t, scan, t.prompt(), 2));
        assertThrows(DataIntegrityViolationException.class, () -> unit(t, scan, t.prompt(), 1));
    }

    @Test
    void onlyOneActiveScanPerProject() {
        Tenant t = tenant();
        scan(t, "running");
        assertThrows(DataIntegrityViolationException.class, () -> scan(t, "queued"));
        assertDoesNotThrow(() -> scan(t, "completed"));
    }

    @Test
    void rowsCannotPointAtAnotherTenantsData() {
        Tenant a = tenant();
        Tenant b = tenant();
        UUID scanA = scan(a, "queued");
        // Unit of account A that references account B's prompt.
        assertThrows(DataIntegrityViolationException.class, () -> unit(a, scanA, b.prompt(), 1));
        // Scan of account A on account B's project.
        assertThrows(DataIntegrityViolationException.class, () -> db.sql("""
                INSERT INTO scan (account_id, project_id, trigger, samples) VALUES (:a, :p, 'manual', 1)
                """).param("a", a.account()).param("p", b.project()).update());
    }

    @Test
    void doneUnitMustReferenceACall() {
        Tenant t = tenant();
        UUID scan = scan(t, "queued");
        unit(t, scan, t.prompt(), 1);
        assertThrows(DataIntegrityViolationException.class,
                () -> db.sql("UPDATE scan_unit SET status = 'done' WHERE scan_id = :s").param("s", scan).update());
    }

    @Test
    void deletingTheAccountRemovesItsScanData() {
        Tenant t = tenant();
        unit(t, scan(t, "queued"), t.prompt(), 1);
        db.sql("DELETE FROM account WHERE id = :a").param("a", t.account()).update();
        for (String table : new String[] {"prompt", "prompt_version", "scan", "scan_unit"}) {
            int left = db.sql("SELECT count(*) FROM " + table + " WHERE account_id = :a")
                    .param("a", t.account()).query(Integer.class).single();
            assertEquals(0, left, table);
        }
    }
}
