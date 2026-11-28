package com.geomaster.core.brands;

import com.geomaster.core.tenant.TenantContext;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Brands and aliases; every query is scoped to the current account (NFR-05). Deletes are soft (REQ-3). */
@Repository
public class BrandRepository {

    private final JdbcClient db;

    public BrandRepository(JdbcClient db) {
        this.db = db;
    }

    /** The own brand of a new project: display name from the domain, the domain as alias (REQ-1). */
    public void createOwn(UUID projectId, String domain) {
        UUID brand = insertBrand(projectId, Brand.OWN, displayName(domain));
        insertAlias(projectId, brand, "domain", domain, BrandNormalizer.domain(domain));
    }

    public UUID insertBrand(UUID projectId, String kind, String name) {
        return db.sql("""
                INSERT INTO brand (account_id, project_id, kind, name)
                VALUES (:accountId, :projectId, :kind, :name)
                RETURNING id
                """)
                .param("accountId", TenantContext.accountId()).param("projectId", projectId)
                .param("kind", kind).param("name", name)
                .query(UUID.class).single();
    }

    public UUID insertAlias(UUID projectId, UUID brandId, String kind, String value, String normalized) {
        return db.sql("""
                INSERT INTO brand_alias (account_id, project_id, brand_id, kind, value, normalized)
                VALUES (:accountId, :projectId, :brandId, :kind, :value, :normalized)
                RETURNING id
                """)
                .param("accountId", TenantContext.accountId()).param("projectId", projectId)
                .param("brandId", brandId).param("kind", kind).param("value", value).param("normalized", normalized)
                .query(UUID.class).single();
    }

    /** Active brands of the project with their active aliases, own brand first. */
    public List<Brand> listActive(UUID projectId) {
        UUID accountId = TenantContext.accountId();
        Map<UUID, Brand> brands = new LinkedHashMap<>();
        db.sql("""
                SELECT id, project_id, kind, name, created_at FROM brand
                WHERE project_id = :projectId AND account_id = :accountId AND deleted_at IS NULL
                ORDER BY kind = 'own' DESC, created_at, id
                """)
                .param("projectId", projectId).param("accountId", accountId)
                .query((rs, n) -> new Brand(rs.getObject("id", UUID.class), rs.getObject("project_id", UUID.class),
                        rs.getString("kind"), rs.getString("name"), List.of(),
                        rs.getObject("created_at", java.time.OffsetDateTime.class)))
                .list()
                .forEach(b -> brands.put(b.id(), b));
        Map<UUID, List<Brand.Alias>> aliases = new LinkedHashMap<>();
        db.sql("""
                SELECT id, brand_id, kind, value, normalized FROM brand_alias
                WHERE project_id = :projectId AND account_id = :accountId AND deleted_at IS NULL
                ORDER BY created_at, id
                """)
                .param("projectId", projectId).param("accountId", accountId)
                .query(Brand.Alias.class).list()
                .forEach(a -> aliases.computeIfAbsent(a.brandId(), k -> new ArrayList<>()).add(a));
        return brands.values().stream().map(b -> b.withAliases(aliases.getOrDefault(b.id(), List.of()))).toList();
    }

    public Optional<Brand> find(UUID projectId, UUID brandId) {
        return listActive(projectId).stream().filter(b -> b.id().equals(brandId)).findFirst();
    }

    public int countActiveCompetitors(UUID projectId) {
        return db.sql("""
                SELECT count(*) FROM brand
                WHERE project_id = :projectId AND account_id = :accountId AND kind = 'competitor' AND deleted_at IS NULL
                """)
                .param("projectId", projectId).param("accountId", TenantContext.accountId())
                .query(Integer.class).single();
    }

    public void rename(UUID brandId, String name) {
        db.sql("UPDATE brand SET name = :name, updated_at = now() WHERE id = :id AND account_id = :accountId")
                .param("name", name).param("id", brandId).param("accountId", TenantContext.accountId()).update();
    }

    /** Soft-delete a competitor and its aliases; past mentions keep their brand_id (REQ-3). */
    public void softDeleteCompetitor(UUID brandId) {
        UUID accountId = TenantContext.accountId();
        db.sql("UPDATE brand_alias SET deleted_at = now() WHERE brand_id = :id AND account_id = :accountId AND deleted_at IS NULL")
                .param("id", brandId).param("accountId", accountId).update();
        db.sql("""
                UPDATE brand SET deleted_at = now(), updated_at = now()
                WHERE id = :id AND account_id = :accountId AND kind = 'competitor'
                """)
                .param("id", brandId).param("accountId", accountId).update();
    }

    public int softDeleteAlias(UUID brandId, UUID aliasId) {
        return db.sql("""
                UPDATE brand_alias SET deleted_at = now()
                WHERE id = :id AND brand_id = :brandId AND account_id = :accountId AND deleted_at IS NULL
                """)
                .param("id", aliasId).param("brandId", brandId).param("accountId", TenantContext.accountId()).update();
    }

    /** A brand or alias changed: scans of the project are re-resolved by the analyser (extraction-analysis REQ-15). */
    public void bumpBrandsVersion(UUID projectId) {
        db.sql("UPDATE project SET brands_version = brands_version + 1 WHERE id = :id AND account_id = :accountId")
                .param("id", projectId).param("accountId", TenantContext.accountId()).update();
    }

    /** "ride-flumserberg.ch" → "Ride Flumserberg": first label, hyphens as spaces, words capitalised. */
    static String displayName(String domain) {
        String label = domain.split("\\.", 2)[0];
        StringBuilder out = new StringBuilder();
        for (String word : label.split("-")) {
            if (word.isEmpty()) {
                continue;
            }
            if (!out.isEmpty()) {
                out.append(' ');
            }
            out.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return out.isEmpty() ? domain : out.toString();
    }
}
