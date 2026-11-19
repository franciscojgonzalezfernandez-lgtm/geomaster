import "server-only";

import { Pool } from "pg";

/** One Postgres pool per server process; Better Auth and the token helper share it. */
export const db = new Pool({ connectionString: process.env.DATABASE_URL });
