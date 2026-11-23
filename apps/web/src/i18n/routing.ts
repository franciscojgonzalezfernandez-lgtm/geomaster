import { defineRouting } from "next-intl/routing";

/** Interface languages (FR-ACC-04, NFR-08). Must match the `app_user.interface_locale` check in V2. */
export const routing = defineRouting({
  locales: ["en", "de", "fr", "es"],
  defaultLocale: "en",
});

export type AppLocale = (typeof routing.locales)[number];

export function isAppLocale(value: unknown): value is AppLocale {
  return typeof value === "string" && (routing.locales as readonly string[]).includes(value);
}
