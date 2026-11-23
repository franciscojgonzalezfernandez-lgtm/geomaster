import type { Metadata } from "next";
import { locale as rootLocale } from "next/root-params";
import { getTranslations } from "next-intl/server";
import { Suspense } from "react";

import { LocaleSwitcher } from "@/components/locale-switcher";
import { localeAlternates } from "@/i18n/alternates";
import { redirect } from "@/i18n/navigation";
import { isAppLocale, routing } from "@/i18n/routing";
import { currentLocale } from "@/lib/locale";
import { getSession } from "@/lib/session";

import { SignInForm } from "./sign-in-form";

export async function generateMetadata(): Promise<Metadata> {
  const t = await getTranslations("SignIn");
  return { title: `${t("title")}`, alternates: localeAlternates(await currentLocale(), "/signin") };
}

/** Already signed in? Go straight to the projects. Reads cookies, so it streams behind Suspense. */
async function RedirectIfSignedIn() {
  const session = await getSession();
  if (session) {
    const l = await rootLocale();
    redirect({ href: "/projects", locale: isAppLocale(l) ? l : routing.defaultLocale });
  }
  return null;
}

export default async function SignInPage() {
  const t = await getTranslations("SignIn");
  const googleEnabled = Boolean(process.env.GOOGLE_CLIENT_ID && process.env.GOOGLE_CLIENT_SECRET);
  return (
    <main id="main" className="mx-auto flex min-h-screen w-full max-w-sm flex-col justify-center gap-6 p-6">
      <Suspense fallback={null}>
        <RedirectIfSignedIn />
      </Suspense>
      <div className="flex items-center justify-between">
        <span className="font-semibold">GEOMASTER</span>
        <LocaleSwitcher />
      </div>
      <div className="flex flex-col gap-2">
        <h1 className="text-2xl font-semibold">{t("title")}</h1>
        <p className="text-neutral-600 dark:text-neutral-400">{t("subtitle")}</p>
      </div>
      <SignInForm googleEnabled={googleEnabled} />
    </main>
  );
}
