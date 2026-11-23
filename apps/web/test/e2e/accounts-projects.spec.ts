import { expect, test, type Page } from "@playwright/test";

import { closeDb, interfaceLocale, magicLinkFromLog } from "./db";

test.afterAll(closeDb);

const uniqueEmail = (tag: string) => `e2e-${tag}-${Date.now()}-${Math.floor(Math.random() * 1e6)}@example.com`;

async function signIn(page: Page, locale: string, email: string) {
  await page.goto(`/${locale}/signin`);
  await page.locator('input[type="email"]').fill(email);
  await page.locator('form button[type="submit"]').click();
  await expect(page.getByRole("status")).toContainText(email);
  const link = await magicLinkFromLog(email);
  expect(link).toContain(`callbackURL=${encodeURIComponent(`/${locale}/projects`)}`);
  await page.goto(link);
  await expect(page).toHaveURL(new RegExp(`/${locale}/projects$`));
}

test("sign in → create project → list (EN)", async ({ page }) => {
  const email = uniqueEmail("en");
  await signIn(page, "en", email);

  await expect(page.getByRole("heading", { level: 1, name: "Projects" })).toBeVisible();
  await expect(page.getByText("No projects yet")).toBeVisible();
  await expect(page.getByText("0 of 1 domains on the Free plan")).toBeVisible();

  await page.getByRole("link", { name: "New project" }).click();
  await page.getByLabel("Domain").fill("https://www.Example-E2E.ch/path");
  await page.getByLabel("Region or city (optional)").fill("Zürich");
  await page.getByRole("button", { name: "Create project" }).click();

  await expect(page).toHaveURL(/\/en\/projects$/);
  const card = page.getByRole("listitem").filter({ hasText: "example-e2e.ch" });
  await expect(card).toBeVisible();
  await expect(card).toContainText("English");
  await expect(card).toContainText("Zürich, Switzerland");
  await expect(page.getByText("1 of 1 domains on the Free plan")).toBeVisible();

  // REQ-12: the Free plan allows one domain; the second is rejected with a localized message.
  await page.getByRole("link", { name: "New project" }).click();
  await page.getByLabel("Domain").fill("second-e2e.ch");
  await page.getByRole("button", { name: "Create project" }).click();
  await expect(page.locator("#form-error")).toHaveText("Your plan allows 1 domain. Upgrade to add more.");
});

test("new users start in their sign-in language; the switcher persists it (REQ-15, REQ-16)", async ({ page }) => {
  const email = uniqueEmail("de");
  await signIn(page, "de", email);
  await expect(page.getByRole("heading", { level: 1, name: "Projekte" })).toBeVisible();
  expect(await interfaceLocale(email)).toBe("de");

  await page.getByLabel("Sprache der Oberfläche").selectOption("fr");
  await expect(page).toHaveURL(/\/fr\/projects$/);
  await expect(page.getByRole("heading", { level: 1, name: "Projets" })).toBeVisible();
  await expect.poll(() => interfaceLocale(email)).toBe("fr");

  // The stored language wins over the URL.
  await page.goto("/en/projects");
  await expect(page).toHaveURL(/\/fr\/projects$/);
});

test("signed-out visitors are sent to sign-in", async ({ page }) => {
  await page.goto("/es/projects");
  await expect(page).toHaveURL(/\/es\/signin$/);
  await expect(page.getByRole("heading", { level: 1 })).toHaveText("Inicia sesión en GEOMASTER");
});
