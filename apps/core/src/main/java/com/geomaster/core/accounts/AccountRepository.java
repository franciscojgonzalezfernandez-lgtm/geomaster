package com.geomaster.core.accounts;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * Accounts are the tenant boundary, so this repository takes an explicit id rather than reading
 * {@code TenantContext}: callers resolve the account before a tenant scope exists (e.g. first
 * sign-in provisioning, T5).
 */
@Repository
public class AccountRepository {

    private final JdbcClient db;

    public AccountRepository(JdbcClient db) {
        this.db = db;
    }

    public Account create(String name, String planId) {
        return db.sql("""
                INSERT INTO account (name, plan_id)
                VALUES (:name, :planId)
                RETURNING id, name, plan_id, created_at
                """)
                .param("name", name)
                .param("planId", planId)
                .query(Account.class)
                .single();
    }

    public Optional<Account> findById(UUID id) {
        return db.sql("SELECT id, name, plan_id, created_at FROM account WHERE id = :id")
                .param("id", id)
                .query(Account.class)
                .optional();
    }
}
