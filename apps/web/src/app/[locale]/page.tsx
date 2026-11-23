import { plans } from "@geomaster/plans";
import { getTranslations } from "next-intl/server";

import type { Metadata } from "next";

import { LocaleSwitcher } from "@/components/locale-switcher";
import { localeAlternates } from "@/i18n/alternates";
import { Link } from "@/i18n/navigation";
import { currentLocale } from "@/lib/locale";

export async function generateMetadata(): Promise<Metadata> {
  return { alternates: localeAlternates(await currentLocale()) };
}

export default async function Home() {
  const t = await getTranslations("Home");
  const selfServe = plans.filter((plan) => plan.selfServe);
  return (
    <main className="mx-auto flex min-h-screen max-w-2xl flex-col justify-center gap-6 p-8">
      <div className="self-end">
        <LocaleSwitcher />
      </div>
      <h1 className="text-4xl font-semibold">GEOMASTER</h1>
      <p className="text-lg">{t("tagline")}</p>
      <p className="text-sm text-neutral-600 dark:text-neutral-400">{t("beta", { count: selfServe.length })}</p>
      <Link
        href="/signin"
        className="self-start rounded-md bg-neutral-900 px-4 py-2 font-medium text-white hover:bg-neutral-700 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-neutral-900 dark:bg-white dark:text-neutral-900 dark:hover:bg-neutral-200"
      >
        {t("cta")}
      </Link>
    </main>
  );
}
