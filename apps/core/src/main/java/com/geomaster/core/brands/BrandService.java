package com.geomaster.core.brands;

import com.geomaster.core.accounts.AccountRepository;
import com.geomaster.core.accounts.DomainNames;
import com.geomaster.core.accounts.ProjectService;
import com.geomaster.core.api.InvalidInputException;
import com.geomaster.core.api.NotFoundException;
import com.geomaster.core.plans.Plans;
import com.geomaster.core.tenant.TenantContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * Own brand and competitors of a project (extraction-analysis REQ-1..REQ-3, REQ-23). A brand's
 * matching keys are its normalised name plus its aliases' keys; no key may belong to two active
 * brands of one project, so resolution is never ambiguous (REQ-2). Writes lock the account row so
 * concurrent edits cannot overshoot the competitor limit or race on keys.
 */
@Service
public class BrandService {

    static final int MAX_NAME = 200;
    static final int MAX_ALIASES_PER_REQUEST = 20;

    /** The project's brands: its own brand and its active competitors. */
    public record ProjectBrands(Brand own, List<Brand> competitors) {}

    /** A new alias: {@code kind} is {@code name} or {@code domain}. */
    public record AliasInput(String kind, String value) {}

    private final BrandRepository brands;
    private final ProjectService projects;
    private final AccountRepository accounts;
    private final Plans plans;

    public BrandService(BrandRepository brands, ProjectService projects, AccountRepository accounts, Plans plans) {
        this.brands = brands;
        this.projects = projects;
        this.accounts = accounts;
        this.plans = plans;
    }

    public ProjectBrands list(UUID projectId) {
        projects.get(projectId); // membership + 404
        return split(brands.listActive(projectId));
    }

    @Transactional
    public Brand addCompetitor(UUID projectId, String rawName, String rawDomain, List<String> rawAliases) {
        projects.get(projectId);
        String planId = lockAccount();
        String name = name(rawName);
        String nameKey = key("name", BrandNormalizer.name(name));
        String domain = rawDomain == null || rawDomain.isBlank() ? null : DomainNames.normalise(rawDomain);
        List<String> aliasNames = rawAliases == null ? List.of() : rawAliases;
        if (aliasNames.size() > MAX_ALIASES_PER_REQUEST) {
            throw new InvalidInputException("aliases", "at most " + MAX_ALIASES_PER_REQUEST + " aliases per request");
        }

        List<Brand> current = brands.listActive(projectId);
        Set<String> taken = keysOf(current, null);
        requireFree(taken, nameKey, "name");
        Set<String> own = new LinkedHashSet<>(Set.of(nameKey));
        String domainKey = null;
        if (domain != null) {
            domainKey = key("domain", BrandNormalizer.domain(domain));
            requireFree(taken, domainKey, "domain");
        }
        plans.requireCompetitors(planId, brands.countActiveCompetitors(projectId) + 1);

        UUID id = brands.insertBrand(projectId, Brand.COMPETITOR, name);
        if (domain != null) {
            brands.insertAlias(projectId, id, "domain", domain, domainKey);
            own.add(domainKey);
        }
        for (String raw : aliasNames) {
            String value = name(raw, "aliases");
            String k = key("aliases", BrandNormalizer.name(value));
            requireFree(taken, k, "aliases");
            if (own.add(k)) { // the same brand written twice in one request is not an error
                brands.insertAlias(projectId, id, "name", value, k);
            }
        }
        return brands.find(projectId, id).orElseThrow();
    }

    @Transactional
    public Brand rename(UUID projectId, UUID brandId, String rawName) {
        projects.get(projectId);
        lockAccount();
        List<Brand> current = brands.listActive(projectId);
        find(current, brandId);
        String name = name(rawName);
        requireFree(keysOf(current, brandId), key("name", BrandNormalizer.name(name)), "name");
        brands.rename(brandId, name);
        return brands.find(projectId, brandId).orElseThrow();
    }

    @Transactional
    public Brand addAlias(UUID projectId, UUID brandId, AliasInput input) {
        projects.get(projectId);
        lockAccount();
        List<Brand> current = brands.listActive(projectId);
        Brand brand = find(current, brandId);
        String kind = input == null || input.kind() == null ? "" : input.kind().trim().toLowerCase(Locale.ROOT);
        String value;
        String k;
        switch (kind) {
            case "name" -> {
                value = name(input.value(), "value");
                k = key("value", BrandNormalizer.name(value));
            }
            case "domain" -> {
                value = DomainNames.normalise(input.value());
                k = key("value", BrandNormalizer.domain(value));
            }
            default -> throw new InvalidInputException("kind", "kind must be one of [name, domain]");
        }
        requireFree(keysOf(current, brandId), k, "value");
        if (brand.aliases().stream().anyMatch(a -> a.normalized().equals(k))) {
            throw new InvalidInputException("value", "this brand already has an alias with the same matching key");
        }
        brands.insertAlias(projectId, brandId, kind, value, k);
        return brands.find(projectId, brandId).orElseThrow();
    }

    @Transactional
    public void deleteCompetitor(UUID projectId, UUID brandId) {
        projects.get(projectId);
        lockAccount();
        Brand brand = find(brands.listActive(projectId), brandId);
        if (Brand.OWN.equals(brand.kind())) {
            throw new InvalidInputException("brandId", "the project's own brand can be renamed but not deleted");
        }
        brands.softDeleteCompetitor(brandId);
    }

    @Transactional
    public void deleteAlias(UUID projectId, UUID brandId, UUID aliasId) {
        projects.get(projectId);
        lockAccount();
        find(brands.listActive(projectId), brandId);
        if (brands.softDeleteAlias(brandId, aliasId) == 0) {
            throw new NotFoundException("alias");
        }
    }

    private String lockAccount() {
        return accounts.lockForMember(TenantContext.accountId(), TenantContext.userId())
                .orElseThrow(() -> new NotFoundException("account")).planId();
    }

    private static ProjectBrands split(List<Brand> all) {
        Brand own = all.stream().filter(b -> Brand.OWN.equals(b.kind())).findFirst()
                .orElseThrow(() -> new IllegalStateException("project without own brand"));
        return new ProjectBrands(own, all.stream().filter(b -> !Brand.OWN.equals(b.kind())).toList());
    }

    private static Brand find(List<Brand> all, UUID brandId) {
        return all.stream().filter(b -> b.id().equals(brandId)).findFirst().orElseThrow(() -> new NotFoundException("brand"));
    }

    /** Matching keys of every active brand except {@code exceptBrandId}. */
    private static Set<String> keysOf(List<Brand> all, UUID exceptBrandId) {
        Set<String> keys = new LinkedHashSet<>();
        for (Brand b : all) {
            if (b.id().equals(exceptBrandId)) {
                continue;
            }
            keys.add(BrandNormalizer.name(b.name()));
            b.aliases().forEach(a -> keys.add(a.normalized()));
        }
        return keys;
    }

    private static void requireFree(Set<String> taken, String key, String field) {
        if (taken.contains(key)) {
            throw new InvalidInputException(field, "matches a brand already in this project");
        }
    }

    private static String key(String field, String key) {
        if (key.isEmpty()) {
            throw new InvalidInputException(field, "must contain letters or digits");
        }
        return key;
    }

    private static String name(String raw) {
        return name(raw, "name");
    }

    private static String name(String raw, String field) {
        String v = raw == null ? "" : raw.strip();
        if (v.isEmpty() || v.length() > MAX_NAME) {
            throw new InvalidInputException(field, field + " must be 1.." + MAX_NAME + " characters");
        }
        return v;
    }
}
