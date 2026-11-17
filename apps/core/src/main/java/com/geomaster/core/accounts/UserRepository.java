package com.geomaster.core.accounts;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/** {@code app_user} access for the authenticated user only (the id always comes from the token). */
@Repository
public class UserRepository {

    private final JdbcClient db;

    public UserRepository(JdbcClient db) {
        this.db = db;
    }

    /** Lock the user's row so concurrent first sign-ins provision exactly one account (REQ-2). */
    public Optional<AppUser> lockById(UUID userId) {
        return db.sql("SELECT id, email, name, interface_locale FROM app_user WHERE id = :id FOR UPDATE")
                .param("id", userId)
                .query(AppUser.class)
                .optional();
    }

    public Optional<AppUser> updateInterfaceLocale(UUID userId, String locale) {
        return db.sql("""
                UPDATE app_user SET interface_locale = :locale, updated_at = now()
                WHERE id = :id
                RETURNING id, email, name, interface_locale
                """)
                .param("id", userId)
                .param("locale", locale)
                .query(AppUser.class)
                .optional();
    }
}
