package com.geomaster.core.tenant;

import java.util.UUID;
import java.util.function.Supplier;

/**
 * Request-scoped tenant identity (ADR-0009/ADR-0017). The {@code ServiceTokenFilter} (T3) binds
 * the current {@link Tenant} for the duration of a request; every tenant-owned query reads
 * {@link #accountId()} so no query can run without an account scope (NFR-05).
 *
 * <p>Backed by a {@link ScopedValue} (JDK 25), which is safe across the virtual-thread-per-request
 * model used by the core service.
 */
public final class TenantContext {

    /** The authenticated tenant carried by a service token: the account being acted on and its plan. */
    public record Tenant(UUID accountId, String planId) {}

    private static final ScopedValue<Tenant> CURRENT = ScopedValue.newInstance();

    private TenantContext() {}

    /** Run {@code body} with {@code tenant} bound as the current tenant. */
    public static void run(Tenant tenant, Runnable body) {
        ScopedValue.where(CURRENT, tenant).run(body);
    }

    /** Evaluate {@code body} with {@code tenant} bound as the current tenant and return its result. */
    public static <T> T supply(Tenant tenant, Supplier<T> body) {
        return ScopedValue.where(CURRENT, tenant).call(() -> body.get());
    }

    /** The current tenant, or fail if none is bound (a query attempted outside a tenant scope). */
    public static Tenant current() {
        if (!CURRENT.isBound()) {
            throw new IllegalStateException("No tenant bound to the current context");
        }
        return CURRENT.get();
    }

    /** The current tenant's account id — the scope every tenant-owned query must filter by. */
    public static UUID accountId() {
        return current().accountId();
    }
}
