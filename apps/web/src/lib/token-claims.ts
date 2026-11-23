/** Lifetime of a service token. Core rejects anything above 2 minutes (ADR-0017). */
export const TOKEN_TTL_SECONDS = 60;

export type ServiceTokenClaims = {
  sub: string;
  iat: number;
  exp: number;
  acct?: string;
  plan?: string;
};

/**
 * Claims of a service token. With an account it carries `acct` and `plan`; without one it is the
 * provisioning token core accepts only on `POST /v1/users/me`. `iss`/`aud` are added by the JWT plugin.
 */
export function serviceTokenClaims(
  userId: string,
  account?: { id: string; planId: string },
  nowMs: number = Date.now(),
): ServiceTokenClaims {
  const iat = Math.floor(nowMs / 1000);
  const claims: ServiceTokenClaims = { sub: userId, iat, exp: iat + TOKEN_TTL_SECONDS };
  if (account) {
    claims.acct = account.id;
    claims.plan = account.planId;
  }
  return claims;
}
