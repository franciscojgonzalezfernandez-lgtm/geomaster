import "server-only";

import { betterAuth } from "better-auth";
import { nextCookies } from "better-auth/next-js";
<<<<<<< HEAD
import { jwt } from "better-auth/plugins";
import { magicLink } from "better-auth/plugins/magic-link";

import { db } from "./db";
=======
import { magicLink } from "better-auth/plugins/magic-link";
import { Pool } from "pg";

>>>>>>> origin/main
import { sendMagicLinkEmail } from "./email";

/**
 * Better Auth (ADR-0017). Tables are created by Flyway in apps/core (V2); web reads and writes
 * them but never migrates (ADR-0005), so every model and field is pinned to the Flyway names.
 * IDs are UUIDs, matching the `uuid` primary keys.
 */
export const auth = betterAuth({
  baseURL: process.env.BETTER_AUTH_URL,
  secret: process.env.BETTER_AUTH_SECRET,
<<<<<<< HEAD
  database: db,
  // Service tokens are minted server-side only (src/lib/core.ts); never hand them to the browser.
  disabledPaths: ["/token"],
=======
  database: new Pool({ connectionString: process.env.DATABASE_URL }),
>>>>>>> origin/main
  advanced: {
    database: { generateId: "uuid" },
  },
  user: {
    modelName: "app_user",
    fields: {
      emailVerified: "email_verified",
      createdAt: "created_at",
      updatedAt: "updated_at",
    },
  },
  session: {
    modelName: "auth_session",
    fields: {
      userId: "user_id",
      expiresAt: "expires_at",
      ipAddress: "ip_address",
      userAgent: "user_agent",
      createdAt: "created_at",
      updatedAt: "updated_at",
    },
    additionalFields: {
      // Tenant for the service token's `acct` claim; set after core provisions the account (T9).
      activeAccountId: { type: "string", required: false, input: false, fieldName: "active_account_id" },
    },
  },
  // Better Auth's "account" is an OAuth provider link; the business `account` is the tenant.
  account: {
    modelName: "auth_identity",
    fields: {
      userId: "user_id",
      providerId: "provider_id",
      accountId: "provider_account_id",
      accessToken: "access_token",
      refreshToken: "refresh_token",
      idToken: "id_token",
      accessTokenExpiresAt: "access_token_expires_at",
      refreshTokenExpiresAt: "refresh_token_expires_at",
      createdAt: "created_at",
      updatedAt: "updated_at",
    },
  },
  verification: {
    modelName: "auth_verification",
    fields: {
      expiresAt: "expires_at",
      createdAt: "created_at",
      updatedAt: "updated_at",
    },
  },
  socialProviders: {
    google: {
      clientId: process.env.GOOGLE_CLIENT_ID ?? "",
      clientSecret: process.env.GOOGLE_CLIENT_SECRET ?? "",
      enabled: Boolean(process.env.GOOGLE_CLIENT_ID && process.env.GOOGLE_CLIENT_SECRET),
    },
  },
<<<<<<< HEAD
  databaseHooks: {
    session: {
      create: {
        // First sign-in: core provisions the personal account; the session remembers it. If core
        // is down, sign-in still succeeds and coreFor() provisions on the first core call.
        before: async (session) => {
          try {
            const { provisionAccount } = await import("./core");
            return { data: { ...session, activeAccountId: await provisionAccount(session.userId) } };
          } catch (e) {
            console.warn(`[auth] account provisioning deferred: ${e instanceof Error ? e.message : String(e)}`);
            return { data: session };
          }
        },
      },
    },
  },
  plugins: [
    // EdDSA/Ed25519 service tokens for core; public keys at /api/auth/jwks (ADR-0017).
    jwt({
      jwks: { keyPairConfig: { alg: "EdDSA", crv: "Ed25519" } },
      jwt: {
        issuer: process.env.SERVICE_TOKEN_ISSUER ?? process.env.BETTER_AUTH_URL,
        audience: process.env.SERVICE_TOKEN_AUDIENCE ?? process.env.BETTER_AUTH_URL,
        expirationTime: "1m",
      },
      disableSettingJwtHeader: true,
      schema: {
        jwks: {
          modelName: "auth_jwks",
          fields: {
            publicKey: "public_key",
            privateKey: "private_key",
            createdAt: "created_at",
            expiresAt: "expires_at",
          },
        },
      },
    }),
=======
  plugins: [
>>>>>>> origin/main
    magicLink({
      expiresIn: 15 * 60,
      sendMagicLink: async ({ email, url }) => sendMagicLinkEmail(email, url),
    }),
    nextCookies(), // must stay last: lets server actions set the session cookie
  ],
});

export type AuthSession = typeof auth.$Infer.Session;
