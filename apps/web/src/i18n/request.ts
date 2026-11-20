import { getRequestConfig } from "next-intl/server";
import { locale as rootLocale } from "next/root-params";

import { loadMessages } from "./messages";
import { isAppLocale, routing } from "./routing";

export default getRequestConfig(async ({ locale: explicit }) => {
  // An explicit locale (e.g. getTranslations({ locale }) in a server action or an email) wins;
  // otherwise the [locale] root segment. Unknown values fall back to the default.
  const candidate = explicit ?? (await rootLocale());
  const locale = isAppLocale(candidate) ? candidate : routing.defaultLocale;
  return { locale, messages: await loadMessages(locale) };
});
