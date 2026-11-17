package com.geomaster.core.accounts;

import com.geomaster.core.api.ForbiddenException;
import com.geomaster.core.api.InvalidInputException;
import com.geomaster.core.api.NotFoundException;
import com.geomaster.core.tenant.TenantContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** First-sign-in provisioning, interface language and the current account (REQ-2, REQ-3, REQ-15). */
@Service
public class AccountService {

    /** Plan id (not a limit) given to every new personal account (REQ-2); limits live in plans.json. */
    static final String SIGN_UP_PLAN_ID = "free";
    static final Set<String> LOCALES = Set.of("en", "de", "fr", "es");

    /** Result of provisioning: the user, their personal account, and whether it was just created. */
    public record Provisioned(AppUser user, Account account, boolean created) {}

    private final UserRepository users;
    private final AccountRepository accounts;

    public AccountService(UserRepository users, AccountRepository accounts) {
        this.users = users;
        this.accounts = accounts;
    }

    /**
     * Ensure the signed-in user has exactly one personal account on the sign-up plan. Idempotent:
     * a returning user gets their existing account (REQ-3). The user row is locked so concurrent
     * first requests cannot create two accounts.
     */
    @Transactional
    public Provisioned provisionCurrentUser() {
        UUID userId = TenantContext.userId();
        AppUser user = users.lockById(userId).orElseThrow(() -> new NotFoundException("user"));
        Optional<Account> existing = accounts.findOwnedBy(userId);
        if (existing.isPresent()) {
            return new Provisioned(user, existing.get(), false);
        }
        String name = user.name() != null && !user.name().isBlank() ? user.name() : user.email();
        Account account = accounts.create(name, SIGN_UP_PLAN_ID);
        accounts.addOwner(account.id(), userId);
        return new Provisioned(user, account, true);
    }

    @Transactional
    public AppUser setInterfaceLocale(String locale) {
        if (locale == null || !LOCALES.contains(locale)) {
            throw new InvalidInputException("interfaceLocale", "interfaceLocale must be one of " + LOCALES);
        }
        return users.updateInterfaceLocale(TenantContext.userId(), locale)
                .orElseThrow(() -> new NotFoundException("user"));
    }

    public Account currentAccount() {
        return accounts.findForMember(TenantContext.accountId(), TenantContext.userId())
                .orElseThrow(() -> new NotFoundException("account"));
    }

    /** Delete the current account and all its data (REQ-18). Only an owner may do it. */
    @Transactional
    public void deleteCurrentAccount() {
        Account account = accounts.lockForMember(TenantContext.accountId(), TenantContext.userId())
                .orElseThrow(() -> new NotFoundException("account"));
        if (!accounts.isOwner(account.id(), TenantContext.userId())) {
            throw new ForbiddenException("only an account owner can delete the account");
        }
        accounts.delete(account.id());
    }
}
