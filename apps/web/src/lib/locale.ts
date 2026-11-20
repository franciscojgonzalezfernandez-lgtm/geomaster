import "server-only";

import { locale as rootLocale } from "next/root-params";

import { isAppLocale, routing, type AppLocale } from "@/i18n/routing";

/** The current `[locale]` segment, already validated by the root layout. */
export async function currentLocale(): Promise<AppLocale> {
  const l = await rootLocale();
  return isAppLocale(l) ? l : routing.defaultLocale;
}
