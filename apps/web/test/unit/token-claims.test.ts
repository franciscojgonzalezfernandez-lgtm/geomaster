import { describe, expect, it } from "vitest";

import { serviceTokenClaims, TOKEN_TTL_SECONDS } from "@/lib/token-claims";

const NOW = Date.UTC(2026, 10, 23, 12, 0, 0, 750);
const USER = "6f1d2c3b-0000-4000-8000-000000000001";

describe("service token claims (ADR-0017)", () => {
  it("carries sub, acct and plan with a 60 s lifetime", () => {
    const claims = serviceTokenClaims(USER, { id: "acct-1", planId: "pro" }, NOW);
    expect(claims).toEqual({
      sub: USER,
      acct: "acct-1",
      plan: "pro",
      iat: Math.floor(NOW / 1000),
      exp: Math.floor(NOW / 1000) + 60,
    });
  });

  it("stays well under core's 2-minute ceiling", () => {
    expect(TOKEN_TTL_SECONDS).toBeLessThanOrEqual(120);
  });

  it("omits acct and plan for the provisioning token", () => {
    const claims = serviceTokenClaims(USER, undefined, NOW);
    expect(claims).not.toHaveProperty("acct");
    expect(claims).not.toHaveProperty("plan");
    expect(claims.sub).toBe(USER);
  });

  it("uses whole seconds", () => {
    const { iat, exp } = serviceTokenClaims(USER, undefined, NOW);
    expect(Number.isInteger(iat)).toBe(true);
    expect(Number.isInteger(exp)).toBe(true);
  });
});
