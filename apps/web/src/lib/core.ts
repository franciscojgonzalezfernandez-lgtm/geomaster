import "server-only";

import type { paths } from "@geomaster/api-contract";
import createClient from "openapi-fetch";

import { auth } from "./auth";
import { db } from "./db";
import { serviceTokenClaims } from "./token-claims";

const coreUrl = () => process.env.CORE_URL ?? "http://localhost:8080";

/**
 * Mints a short-lived EdDSA service token. With an account it carries `acct` and `plan`; without
 * one it is the provisioning token core accepts only on `POST /v1/users/me`.
 */
async function mintServiceToken(userId: string, account?: { id: string; planId: string }): Promise<string> {
  const { token } = await auth.api.signJWT({ body: { payload: serviceTokenClaims(userId, account) } });
  return token;
}

/** A typed core client whose every request carries the given bearer token. */
function clientWithToken(token: string) {
  return createClient<paths>({
    baseUrl: coreUrl(),
    headers: { Authorization: `Bearer ${token}` },
  });
}

/**
 * First-sign-in provisioning: core creates the user's personal account (idempotent; 201 or 200)
 * and returns it. Called from the session-create hook and as a fallback before any core call.
 */
export async function provisionAccount(userId: string): Promise<string> {
  const core = clientWithToken(await mintServiceToken(userId));
  const { data, response } = await core.POST("/v1/users/me");
  const accountId = data?.account?.id;
  if (!accountId) {
    throw new Error(`core provisioning failed: HTTP ${response.status}`);
  }
  return accountId;
}

/**
 * The plan is read at mint time, never cached in the session, so a plan change applies to the
 * next request. The join also re-checks membership (defence in depth; core checks it too).
 */
async function accountForMember(accountId: string, userId: string): Promise<{ id: string; planId: string } | null> {
  const { rows } = await db.query<{ id: string; plan_id: string }>(
    `SELECT a.id, a.plan_id
       FROM account a JOIN account_member m ON m.account_id = a.id
      WHERE a.id = $1 AND m.user_id = $2`,
    [accountId, userId],
  );
  return rows[0] ? { id: rows[0].id, planId: rows[0].plan_id } : null;
}

/** Session shape the helper needs; matches Better Auth's session with our additional field. */
export type CoreSession = {
  user: { id: string };
  session: { id: string; activeAccountId?: string | null };
};

/**
 * Typed core client for the signed-in user's active account. Provisions the account first when
 * the session has none (e.g. core was unreachable during sign-in) and stores it on the session.
 */
export async function coreFor(s: CoreSession) {
  let accountId = s.session.activeAccountId ?? null;
  if (!accountId) {
    accountId = await provisionAccount(s.user.id);
    await db.query("UPDATE auth_session SET active_account_id = $1 WHERE id = $2", [accountId, s.session.id]);
  }
  const account = await accountForMember(accountId, s.user.id);
  if (!account) {
    throw new Error("active account not found for this user");
  }
  return clientWithToken(await mintServiceToken(s.user.id, account));
}
