package com.geomaster.core.scan;

import com.geomaster.core.accounts.AccountRepository;
import com.geomaster.core.api.NotFoundException;
import com.geomaster.core.tenant.TenantContext;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

/** Scans as the signed-in user sees them (REQ-16, REQ-17): start, list, detail — account-scoped. */
@Service
public class ScanService {

    static final int LIST_LIMIT = 50;

    private final ScanPlanner planner;
    private final ScanReadRepository scans;
    private final AccountRepository accounts;

    public ScanService(ScanPlanner planner, ScanReadRepository scans, AccountRepository accounts) {
        this.planner = planner;
        this.scans = scans;
        this.accounts = accounts;
    }

    /** Manual start: the planner enforces frequency, one active scan and the budget guard. */
    public ScanView start(UUID projectId) {
        requireMembership();
        ScanPlan plan = planner.start(TenantContext.accountId(), projectId, ScanPlanner.Trigger.MANUAL);
        return scans.find(plan.scanId()).orElseThrow(() -> new IllegalStateException("scan vanished: " + plan.scanId()));
    }

    public List<ScanView> list(UUID projectId) {
        requireMembership();
        if (!projectExists(projectId)) {
            throw new NotFoundException("project");
        }
        return scans.listForProject(projectId, LIST_LIMIT);
    }

    public ScanView get(UUID scanId) {
        requireMembership();
        return scans.find(scanId).orElseThrow(() -> new NotFoundException("scan"));
    }

    private boolean projectExists(UUID projectId) {
        return scans.projectInAccount(projectId);
    }

    /** Defence in depth: the user must belong to the account named in the token. */
    private void requireMembership() {
        accounts.findForMember(TenantContext.accountId(), TenantContext.userId())
                .orElseThrow(() -> new NotFoundException("account"));
    }
}
