import { getTranslations } from "next-intl/server";
import { Suspense } from "react";

import { Link } from "@/i18n/navigation";

import { LocaleSwitcher } from "./locale-switcher";
import { SignOutButton } from "./sign-out-button";

/** Header of the signed-in app. Static: it reads no session, so it stays in the prerendered shell. */
export async function AppHeader() {
  const t = await getTranslations("Nav");
  return (
    <header className="border-b border-neutral-200 dark:border-neutral-800">
      <a href="#main" className="sr-only focus:not-sr-only focus:absolute focus:p-2">
        {t("skipToContent")}
      </a>
      <nav className="mx-auto flex max-w-4xl items-center justify-between gap-4 p-4">
        <Link href="/projects" className="font-semibold">
          GEOMASTER
        </Link>
        <div className="flex items-center gap-4">
          <Link href="/projects" className="text-sm underline-offset-4 hover:underline">
            {t("projects")}
          </Link>
          {/* usePathname() is runtime data on dynamic routes such as /projects/[id]. */}
          <Suspense fallback={<span className="w-24" aria-hidden />}>
            <LocaleSwitcher />
          </Suspense>
          <SignOutButton />
        </div>
      </nav>
    </header>
  );
}
