package com.geomaster.core.auth;

import com.geomaster.core.tenant.TenantContext;
import com.geomaster.core.tenant.TenantContext.Tenant;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Authenticates every {@code /v1/**} request with the web→core service token (REQ-4/REQ-5,
 * ADR-0017). A missing or invalid token gets 401 before any handler — and so before any read or
 * write — runs. A valid token binds the {@link Tenant} for the rest of the request, which every
 * tenant-owned query reads (REQ-6).
 */
public final class ServiceTokenFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(ServiceTokenFilter.class);
    private static final String BEARER = "Bearer ";

    private final ServiceTokenVerifier verifier;

    public ServiceTokenFilter(ServiceTokenVerifier verifier) {
        this.verifier = verifier;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header == null || !header.regionMatches(true, 0, BEARER, 0, BEARER.length())) {
            unauthorized(response, "missing bearer token");
            return;
        }
        ServiceTokenVerifier.VerifiedToken token;
        try {
            token = verifier.verify(header.substring(BEARER.length()).trim());
        } catch (InvalidServiceTokenException e) {
            // Reason is logged, never echoed to the caller; the token itself is never logged.
            log.debug("Rejected service token: {}", e.getMessage());
            unauthorized(response, "invalid token");
            return;
        }
        Tenant tenant = new Tenant(token.userId(), token.accountId(), token.planId());
        try {
            TenantContext.runChecked(tenant, () -> chain.doFilter(request, response));
        } catch (IOException | ServletException | RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new ServletException(e);
        }
    }

    private static void unauthorized(HttpServletResponse response, String detail) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.getWriter().write(
                "{\"type\":\"about:blank\",\"title\":\"Unauthorized\",\"status\":401,\"detail\":\"" + detail + "\"}");
    }
}
