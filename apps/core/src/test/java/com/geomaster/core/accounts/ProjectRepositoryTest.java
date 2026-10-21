package com.geomaster.core.accounts;

import com.geomaster.core.TestcontainersConfiguration;
import com.geomaster.core.tenant.TenantContext;
import com.geomaster.core.tenant.TenantContext.Tenant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class ProjectRepositoryTest {

    @Autowired
    AccountRepository accounts;

    @Autowired
    ProjectRepository projects;

    @Test
    void createIsIdempotentOnDomain() {
        Account a = accounts.create("Acct A", "free");
        Project first = TenantContext.supply(tenant(a.id()),
                () -> projects.create("ride.ch", "CH", "SG", List.of("de", "en")));
        Project again = TenantContext.supply(tenant(a.id()),
                () -> projects.create("ride.ch", "CH", "SG", List.of("de")));

        assertEquals(first.id(), again.id(), "same domain in same account must not duplicate");
        List<String> langs = TenantContext.supply(tenant(a.id()), () -> projects.languagesOf(first.id()));
        assertEquals(List.of("de", "en"), langs);
    }

    @Test
    void tenantIsolationHidesOtherAccountsProjects() {
        Account a = accounts.create("Acct A", "free");
        Account b = accounts.create("Acct B", "free");

        UUID projectId = TenantContext.supply(tenant(a.id()),
                () -> projects.create("flumserberg.ch", "CH", null, List.of("de"))).id();

        Optional<Project> asOwner = TenantContext.supply(tenant(a.id()), () -> projects.findById(projectId));
        Optional<Project> asOther = TenantContext.supply(tenant(b.id()), () -> projects.findById(projectId));

        assertTrue(asOwner.isPresent(), "owner account sees its project");
        assertFalse(asOther.isPresent(), "other account must not see it");

        List<Project> otherList = TenantContext.supply(tenant(b.id()), projects::listForCurrentAccount);
        assertTrue(otherList.isEmpty(), "other account lists none of account A's projects");
    }

    private static Tenant tenant(UUID accountId) {
        return new Tenant(accountId, "free");
    }
}
