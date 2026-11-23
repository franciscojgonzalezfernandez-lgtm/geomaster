package com.geomaster.core.budget;

import java.util.UUID;

/** Default guard until the budget-guard-backoffice spec lands: every scan may run. */
public final class AllowAllBudgetGuard implements BudgetGuard {

    @Override
    public Decision allowScan(UUID accountId, String planId) {
        return Decision.allow();
    }
}
