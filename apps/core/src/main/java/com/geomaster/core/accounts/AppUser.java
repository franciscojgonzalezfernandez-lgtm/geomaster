package com.geomaster.core.accounts;

import java.util.UUID;

/** The Better Auth user row ({@code app_user}); web creates it at sign-in, core reads and updates its locale. */
public record AppUser(UUID id, String email, String name, String interfaceLocale) {}
