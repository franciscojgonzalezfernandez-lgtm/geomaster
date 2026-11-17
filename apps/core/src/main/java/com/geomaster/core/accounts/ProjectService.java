package com.geomaster.core.accounts;

import com.geomaster.core.api.InvalidInputException;
import com.geomaster.core.api.NotFoundException;
import com.geomaster.core.plans.Plans;
import com.geomaster.core.tenant.TenantContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/** Project creation with validation, normalisation, idempotency and plan limits (REQ-8..REQ-13). */
@Service
public class ProjectService {

    private static final Set<String> LANGUAGES = Set.of("en", "de", "fr", "es");
    private static final Pattern COUNTRY = Pattern.compile("^[A-Z]{2}$");
    private static final int MAX_REGION_LENGTH = 100;

    /** A project plus its measurement languages. */
    public record ProjectView(Project project, List<String> languages) {}

    /** Outcome of a create: {@code created=false} means an existing project was returned (REQ-10). */
    public record CreateResult(ProjectView view, boolean created) {}

    private final ProjectRepository projects;
    private final AccountRepository accounts;
    private final Plans plans;

    public ProjectService(ProjectRepository projects, AccountRepository accounts, Plans plans) {
        this.projects = projects;
        this.accounts = accounts;
        this.plans = plans;
    }

    @Transactional
    public CreateResult create(String rawDomain, List<String> rawLanguages, String rawCountry, String rawRegion) {
        String domain = DomainNames.normalise(rawDomain);
        List<String> languages = languages(rawLanguages);
        String country = country(rawCountry);
        String region = region(rawRegion);

        // Lock the account: plan-limit checks and the insert happen as one step per account.
        Account account = accounts.lockForMember(TenantContext.accountId(), TenantContext.userId())
                .orElseThrow(() -> new NotFoundException("account"));

        Optional<Project> existing = projects.findByDomain(domain);
        if (existing.isPresent()) {
            return new CreateResult(view(existing.get()), false);
        }
        plans.requireLanguages(account.planId(), languages.size());
        plans.requireDomains(account.planId(), projects.countActiveForCurrentAccount() + 1);
        return new CreateResult(view(projects.create(domain, country, region, languages)), true);
    }

    public List<ProjectView> list() {
        requireMembership();
        List<ProjectView> out = new ArrayList<>();
        for (Project p : projects.listForCurrentAccount()) {
            out.add(view(p));
        }
        return out;
    }

    public ProjectView get(UUID id) {
        requireMembership();
        return projects.findById(id).map(this::view).orElseThrow(() -> new NotFoundException("project"));
    }

    /** Defence in depth: the user must belong to the account named in the token (as on create). */
    private void requireMembership() {
        accounts.findForMember(TenantContext.accountId(), TenantContext.userId())
                .orElseThrow(() -> new NotFoundException("account"));
    }

    private ProjectView view(Project p) {
        return new ProjectView(p, projects.languagesOf(p.id()));
    }

    private static List<String> languages(List<String> raw) {
        if (raw == null || raw.isEmpty()) {
            throw new InvalidInputException("languages", "at least one measurement language is required");
        }
        Set<String> out = new LinkedHashSet<>();
        for (String l : raw) {
            String v = l == null ? "" : l.trim().toLowerCase(Locale.ROOT);
            if (!LANGUAGES.contains(v)) {
                throw new InvalidInputException("languages", "languages must be from " + LANGUAGES);
            }
            out.add(v);
        }
        return List.copyOf(out);
    }

    private static String country(String raw) {
        String v = raw == null ? "" : raw.trim().toUpperCase(Locale.ROOT);
        if (!COUNTRY.matcher(v).matches()) {
            throw new InvalidInputException("country", "country must be an ISO 3166-1 alpha-2 code");
        }
        return v;
    }

    private static String region(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String v = raw.trim();
        if (v.length() > MAX_REGION_LENGTH) {
            throw new InvalidInputException("region", "region must be at most " + MAX_REGION_LENGTH + " characters");
        }
        return v;
    }
}
