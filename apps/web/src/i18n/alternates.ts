import type { Metadata } from "next";

import { routing } from "./routing";

/** hreflang alternates for a public page path (e.g. "/signin"), resolved against metadataBase (NFR-08/09). */
export function localeAlternates(locale: string, path = ""): Metadata["alternates"] {
  return {
    canonical: `/${locale}${path}`,
    languages: {
      ...Object.fromEntries(routing.locales.map((l) => [l, `/${l}${path}`])),
      "x-default": `/${routing.defaultLocale}${path}`,
    },
  };
}
