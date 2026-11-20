import { isAppLocale, routing, type AppLocale } from "./routing";

/** Name of next-intl's locale cookie (set by the proxy and the language switcher). */
export const LOCALE_COOKIE = "NEXT_LOCALE";

/** Best supported locale from an `Accept-Language` header, by q-value; `null` if none match. */
export function fromAcceptLanguage(header: string | null | undefined): AppLocale | null {
  if (!header) return null;
  const ranked = header
    .split(",")
    .map((part, index) => {
      const [tag, ...params] = part.trim().split(";");
      const q = params.map((p) => p.trim()).find((p) => p.startsWith("q="));
      return { base: tag.trim().toLowerCase().split("-")[0], q: q ? Number(q.slice(2)) : 1, index };
    })
    .filter((r) => r.base && Number.isFinite(r.q) && r.q > 0)
    .sort((a, b) => b.q - a.q || a.index - b.index);
  return ranked.map((r) => r.base).find(isAppLocale) ?? null;
}

function cookieValue(cookieHeader: string | null | undefined, name: string): string | undefined {
  return cookieHeader
    ?.split(";")
    .map((c) => c.trim().split("="))
    .find(([k]) => k === name)?.[1];
}

/** Same precedence as the proxy: locale cookie, then Accept-Language, then EN (REQ-16). */
export function localeFromHeaders(headers: Headers | null | undefined): AppLocale {
  const cookie = cookieValue(headers?.get("cookie"), LOCALE_COOKIE);
  if (isAppLocale(cookie)) return cookie;
  return fromAcceptLanguage(headers?.get("accept-language")) ?? routing.defaultLocale;
}
