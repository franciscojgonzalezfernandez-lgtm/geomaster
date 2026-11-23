import { describe, expect, it } from "vitest";

import { fromAcceptLanguage, localeFromHeaders } from "@/i18n/negotiate";

describe("interface language negotiation (REQ-16)", () => {
  it("picks the highest-q supported language", () => {
    expect(fromAcceptLanguage("de-CH,de;q=0.9,en;q=0.8")).toBe("de");
    expect(fromAcceptLanguage("it-CH, fr;q=0.7, en;q=0.9")).toBe("en");
    expect(fromAcceptLanguage("es-ES")).toBe("es");
  });

  it("returns null when nothing matches", () => {
    expect(fromAcceptLanguage("it,ja;q=0.5")).toBeNull();
    expect(fromAcceptLanguage("")).toBeNull();
    expect(fromAcceptLanguage(undefined)).toBeNull();
  });

  it("ignores languages with q=0", () => {
    expect(fromAcceptLanguage("fr;q=0, de;q=0.1")).toBe("de");
  });

  it("prefers the locale cookie over Accept-Language, then falls back to EN", () => {
    expect(localeFromHeaders(new Headers({ cookie: "a=1; NEXT_LOCALE=fr", "accept-language": "de" }))).toBe("fr");
    expect(localeFromHeaders(new Headers({ cookie: "NEXT_LOCALE=xx", "accept-language": "es" }))).toBe("es");
    expect(localeFromHeaders(new Headers({ "accept-language": "it" }))).toBe("en");
    expect(localeFromHeaders(null)).toBe("en");
  });
});
