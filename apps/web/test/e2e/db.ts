import { readFile } from "node:fs/promises";

import { Pool } from "pg";

const pool = new Pool({
  connectionString: process.env.DATABASE_URL ?? "postgres://geomaster:geomaster@localhost:5432/geomaster",
});

export async function interfaceLocale(email: string): Promise<string | undefined> {
  const { rows } = await pool.query<{ interface_locale: string }>(
    "SELECT interface_locale FROM app_user WHERE email = $1",
    [email],
  );
  return rows[0]?.interface_locale;
}

export const closeDb = () => pool.end();

/**
 * The magic link the web logged for this email (EMAIL_TRANSPORT=log). Better Auth stores only a
 * hash of the token, so the server log stands in for the inbox. WEB_LOG is the web's stdout file.
 */
export async function magicLinkFromLog(email: string): Promise<string> {
  const file = process.env.WEB_LOG;
  if (!file) throw new Error("WEB_LOG must point at the web server's log file");
  for (let i = 0; i < 40; i++) {
    const lines = (await readFile(file, "utf8")).split("\n");
    const line = lines.reverse().find((l) => l.startsWith(`[magic-link] ${email} `));
    if (line) return new URL(line.split(" ").at(-1)!).pathname + new URL(line.split(" ").at(-1)!).search;
    await new Promise((r) => setTimeout(r, 250));
  }
  throw new Error(`no magic link logged for ${email}`);
}
