package com.geomaster.core.accounts;

import com.geomaster.core.tenant.TenantContext;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Every query is scoped to the current tenant's {@code account_id} (NFR-05); a project owned by
 * another account is invisible here (reads return empty → the API maps that to 404, REQ-7).
 */
@Repository
public class ProjectRepository {

    private final JdbcClient db;

    public ProjectRepository(JdbcClient db) {
        this.db = db;
    }

    /**
     * Create a project for the current account. Idempotent on {@code (account_id, domain)}:
     * re-creating the same domain returns the existing project rather than a duplicate (REQ-10).
     */
    public Project create(String domain, String country, String region, List<String> locales) {
        UUID accountId = TenantContext.accountId();
        UUID id = db.sql("""
                INSERT INTO project (account_id, domain, country, region)
                VALUES (:accountId, :domain, :country, :region)
                ON CONFLICT (account_id, domain) DO UPDATE SET domain = EXCLUDED.domain
                RETURNING id
                """)
                .param("accountId", accountId)
                .param("domain", domain)
                .param("country", country)
                .param("region", region)
                .query(UUID.class)
                .single();
        for (String locale : locales) {
            db.sql("""
                    INSERT INTO project_language (project_id, locale)
                    VALUES (:projectId, :locale)
                    ON CONFLICT DO NOTHING
                    """)
                    .param("projectId", id)
                    .param("locale", locale)
                    .update();
        }
        return findById(id).orElseThrow();
    }

    public Optional<Project> findById(UUID projectId) {
        UUID accountId = TenantContext.accountId();
        return db.sql("""
                SELECT id, account_id, domain, country, region, status, created_at
                FROM project
                WHERE id = :id AND account_id = :accountId
                """)
                .param("id", projectId)
                .param("accountId", accountId)
                .query(Project.class)
                .optional();
    }

    public List<Project> listForCurrentAccount() {
        UUID accountId = TenantContext.accountId();
        return db.sql("""
                SELECT id, account_id, domain, country, region, status, created_at
                FROM project
                WHERE account_id = :accountId
                ORDER BY created_at
                """)
                .param("accountId", accountId)
                .query(Project.class)
                .list();
    }

    /** Measurement languages for a project the current account owns. */
    public List<String> languagesOf(UUID projectId) {
        UUID accountId = TenantContext.accountId();
        return db.sql("""
                SELECT pl.locale
                FROM project_language pl
                JOIN project p ON p.id = pl.project_id
                WHERE pl.project_id = :id AND p.account_id = :accountId
                ORDER BY pl.locale
                """)
                .param("id", projectId)
                .param("accountId", accountId)
                .query(String.class)
                .list();
    }
}
