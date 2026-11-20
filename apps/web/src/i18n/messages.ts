import type en from "@geomaster/i18n/messages/en.json";

import type { AppLocale } from "./routing";

export type Messages = typeof en;

/** Loads one locale's UI strings from packages/i18n (server only; nothing ships to the client unless passed). */
export async function loadMessages(locale: AppLocale): Promise<Messages> {
  switch (locale) {
    case "de":
      return (await import("@geomaster/i18n/messages/de.json")).default;
    case "fr":
      return (await import("@geomaster/i18n/messages/fr.json")).default;
    case "es":
      return (await import("@geomaster/i18n/messages/es.json")).default;
    default:
      return (await import("@geomaster/i18n/messages/en.json")).default;
  }
}
