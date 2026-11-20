import "server-only";

import { headers } from "next/headers";

import { redirect } from "@/i18n/navigation";
import { isAppLocale, type AppLocale } from "@/i18n/routing";

import { auth } from "./auth";

/** The Better Auth session for this request, or `null` (reads cookies: call inside a Suspense boundary). */
export async function getSession() {
  return auth.api.getSession({ headers: await headers() });
}

/**
 * Signed-in session or a redirect to the sign-in page. With `path`, also enforces REQ-16: a signed-in
 * user sees the app in their stored interface language, so a URL in another locale is redirected.
 */
export async function requireSession(locale: AppLocale, path?: string) {
  const session = await getSession();
  if (!session) {
    redirect({ href: "/signin", locale });
  }
  const stored = (session as { user: { interfaceLocale?: string | null } }).user.interfaceLocale;
  if (path && isAppLocale(stored) && stored !== locale) {
    redirect({ href: path, locale: stored });
  }
  return session!;
}
