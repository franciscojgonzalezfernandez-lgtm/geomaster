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

    /** The user's personal account: the oldest one they own (REQ-3). */
    public Optional<Account> findOwnedBy(UUID userId) {
        return db.sql("""
                SELECT a.id, a.name, a.plan_id, a.created_at
                FROM account a JOIN account_member m ON m.account_id = a.id
                WHERE m.user_id = :userId AND m.role = 'owner'
                ORDER BY a.created_at, a.id
                LIMIT 1
                """)
                .param("userId", userId)
                .query(Account.class)
                .optional();
    }

    public void addOwner(UUID accountId, UUID userId) {
        db.sql("INSERT INTO account_member (account_id, user_id, role) VALUES (:accountId, :userId, 'owner')")
                .param("accountId", accountId)
                .param("userId", userId)
                .update();
    }

    /** The account, only if {@code userId} is a member of it (defence in depth on the token's claim). */
    public Optional<Account> findForMember(UUID accountId, UUID userId) {
        return db.sql("""
                SELECT a.id, a.name, a.plan_id, a.created_at
                FROM account a JOIN account_member m ON m.account_id = a.id
                WHERE a.id = :accountId AND m.user_id = :userId
                """)
                .param("accountId", accountId)
                .param("userId", userId)
                .query(Account.class)
                .optional();
    }

    /** Lock the account row so concurrent creates cannot overshoot a plan limit together. */
    public Optional<Account> lockForMember(UUID accountId, UUID userId) {
        return db.sql("""
                SELECT a.id, a.name, a.plan_id, a.created_at
                FROM account a JOIN account_member m ON m.account_id = a.id
                WHERE a.id = :accountId AND m.user_id = :userId
                FOR UPDATE OF a
                """)
                .param("accountId", accountId)
                .param("userId", userId)
                .query(Account.class)
                .optional();
    }

    public Optional<Account> findById(UUID id) {
        return db.sql("SELECT id, name, plan_id, created_at FROM account WHERE id = :id")
                .param("id", id)
                .query(Account.class)
                .optional();
    }
}
