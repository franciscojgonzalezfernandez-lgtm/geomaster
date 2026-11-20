"use server";

import { cookies } from "next/headers";

import { LOCALE_COOKIE } from "@/i18n/negotiate";
import { isAppLocale } from "@/i18n/routing";

import { coreFor } from "./core";
import { getSession } from "./session";

/**
 * Language switcher (REQ-15): persists the interface language on `app_user` through core and
 * updates the locale cookie, so the proxy and the next sign-in agree with it.
 */
export async function setInterfaceLocale(locale: string): Promise<{ ok: boolean }> {
  if (!isAppLocale(locale)) return { ok: false };
  const session = await getSession();
  if (session) {
    const core = await coreFor(session);
    const { response } = await core.PATCH("/v1/users/me", { body: { interfaceLocale: locale } });
    if (!response.ok) return { ok: false };
  }
  (await cookies()).set(LOCALE_COOKIE, locale, { path: "/", sameSite: "lax", maxAge: 60 * 60 * 24 * 365 });
  return { ok: true };
}
