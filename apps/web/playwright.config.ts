import { defineConfig, devices } from "@playwright/test";

/**
 * End-to-end (T11): runs against an already running web (:3000) + core (:8080) + Postgres, as in
 * the CI smoke job or `pnpm dev` locally. The web must use EMAIL_TRANSPORT=log.
 */
export default defineConfig({
  testDir: "test/e2e",
  timeout: 60_000,
  retries: process.env.CI ? 1 : 0,
  reporter: process.env.CI ? [["github"], ["list"]] : "list",
  use: {
    baseURL: process.env.E2E_BASE_URL ?? "http://localhost:3000",
    trace: "retain-on-failure",
  },
  projects: [{ name: "chromium", use: { ...devices["Desktop Chrome"] } }],
});
