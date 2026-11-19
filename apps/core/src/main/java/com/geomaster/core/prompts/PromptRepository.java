package com.geomaster.core.prompts;

import com.geomaster.core.tenant.TenantContext;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Prompts and their versions; every query is scoped to the current account (NFR-05). */
@Repository
public class PromptRepository {

    private static final String SELECT = """
            SELECT p.id, p.project_id, p.locale, p.topic, p.current_version, v.text, p.created_at, p.updated_at
            FROM prompt p
            JOIN prompt_version v ON v.prompt_id = p.id AND v.version = p.current_version
            """;

    private final JdbcClient db;

    public PromptRepository(JdbcClient db) {
        this.db = db;
    }

    public Prompt create(UUID projectId, String locale, String topic, String text) {
        UUID accountId = TenantContext.accountId();
        UUID id = db.sql("""
                INSERT INTO prompt (account_id, project_id, locale, topic)
                VALUES (:accountId, :projectId, :locale, :topic)
                RETURNING id
                """)
                .param("accountId", accountId).param("projectId", projectId)
                .param("locale", locale).param("topic", topic)
                .query(UUID.class).single();
        insertVersion(id, 1, text);
        return find(projectId, id).orElseThrow();
    }

    public Optional<Prompt> find(UUID projectId, UUID promptId) {
        return db.sql(SELECT + " WHERE p.id = :id AND p.project_id = :projectId AND p.account_id = :accountId AND p.deleted_at IS NULL")
                .param("id", promptId).param("projectId", projectId).param("accountId", TenantContext.accountId())
                .query(Prompt.class).optional();
    }

    /** Lock the prompt row for an edit (versions must be sequential). */
    public Optional<Prompt> lock(UUID projectId, UUID promptId) {
        return db.sql(SELECT + " WHERE p.id = :id AND p.project_id = :projectId AND p.account_id = :accountId AND p.deleted_at IS NULL FOR UPDATE OF p")
                .param("id", promptId).param("projectId", projectId).param("accountId", TenantContext.accountId())
                .query(Prompt.class).optional();
    }

    public List<Prompt> listActive(UUID projectId) {
        return db.sql(SELECT + " WHERE p.project_id = :projectId AND p.account_id = :accountId AND p.deleted_at IS NULL ORDER BY p.created_at, p.id")
                .param("projectId", projectId).param("accountId", TenantContext.accountId())
                .query(Prompt.class).list();
    }

    /** Active prompts across all of the account's projects (the plan's `prompts` limit is per account). */
    public int countActiveForAccount() {
        return db.sql("SELECT count(*) FROM prompt WHERE account_id = :accountId AND deleted_at IS NULL")
                .param("accountId", TenantContext.accountId()).query(Integer.class).single();
    }

    /** New text → new version; past answers keep pointing at the old one (REQ-2, NFR-13). */
    public void newVersion(UUID promptId, int version, String text) {
        insertVersion(promptId, version, text);
        db.sql("UPDATE prompt SET current_version = :v, updated_at = now() WHERE id = :id AND account_id = :accountId")
                .param("v", version).param("id", promptId).param("accountId", TenantContext.accountId()).update();
    }

    public void setTopic(UUID promptId, String topic) {
        db.sql("UPDATE prompt SET topic = :topic, updated_at = now() WHERE id = :id AND account_id = :accountId")
                .param("topic", topic).param("id", promptId).param("accountId", TenantContext.accountId()).update();
    }

    public void softDelete(UUID promptId) {
        db.sql("UPDATE prompt SET deleted_at = now(), updated_at = now() WHERE id = :id AND account_id = :accountId")
                .param("id", promptId).param("accountId", TenantContext.accountId()).update();
    }

    private void insertVersion(UUID promptId, int version, String text) {
        db.sql("""
                INSERT INTO prompt_version (prompt_id, version, account_id, text)
                VALUES (:promptId, :version, :accountId, :text)
                """)
                .param("promptId", promptId).param("version", version)
                .param("accountId", TenantContext.accountId()).param("text", text)
                .update();
    }
}
