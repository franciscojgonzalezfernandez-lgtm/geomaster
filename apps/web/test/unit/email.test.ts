import { describe, expect, it } from "vitest";

import { localeFromMagicLink, magicLinkEmail } from "@/lib/email";

const link = (callback: string) =>
  `http://localhost:3000/api/auth/magic-link/verify?token=t0k&callbackURL=${encodeURIComponent(callback)}`;

describe("magic-link email", () => {
  it("takes the locale from the callback URL", () => {
    expect(localeFromMagicLink(link("/de/projects"))).toBe("de");
    expect(localeFromMagicLink(link("/fr/projects/new"))).toBe("fr");
  });

  it("falls back to EN for unknown or missing locales", () => {
    expect(localeFromMagicLink(link("/xx/projects"))).toBe("en");
    expect(localeFromMagicLink("http://localhost:3000/api/auth/magic-link/verify?token=t0k")).toBe("en");
    expect(localeFromMagicLink("not a url")).toBe("en");
  });

  it("is localized in all four languages and contains the link", async () => {
    const subjects = new Set<string>();
    for (const locale of ["en", "de", "fr", "es"] as const) {
      const mail = await magicLinkEmail(link(`/${locale}/projects`), locale);
      subjects.add(mail.subject);
      expect(mail.html).toContain(`lang="${locale}"`);
      expect(mail.text).toContain("token=t0k");
    }
    expect(subjects.size).toBe(4);
  });

  it("escapes the URL in the HTML body", async () => {
    const mail = await magicLinkEmail('http://x.test/?a="><script>', "en");
    expect(mail.html).not.toContain("<script>");
    expect(mail.html).toContain("&quot;&gt;&lt;script&gt;");
  });
});
