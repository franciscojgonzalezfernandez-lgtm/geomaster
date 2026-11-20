"use client";

import { useLocale, useTranslations } from "next-intl";
import { useTransition } from "react";

import { usePathname, useRouter } from "@/i18n/navigation";
import { routing, type AppLocale } from "@/i18n/routing";
import { setInterfaceLocale } from "@/lib/locale-action";

/** Each language is named in itself (Deutsch, Français, …), so no per-locale strings are needed. */
const endonym = (l: AppLocale) => {
  const name = new Intl.DisplayNames([l], { type: "language" }).of(l) ?? l;
  return name.charAt(0).toLocaleUpperCase(l) + name.slice(1);
};

export function LocaleSwitcher() {
  const t = useTranslations("Nav");
  const locale = useLocale();
  const router = useRouter();
  const pathname = usePathname();
  const [pending, startTransition] = useTransition();

  function onChange(next: AppLocale) {
    startTransition(async () => {
      await setInterfaceLocale(next);
      router.replace(pathname, { locale: next });
    });
  }

  return (
    <select
        aria-label={t("language")}
        value={locale}
        disabled={pending}
        onChange={(e) => onChange(e.target.value as AppLocale)}
        className="rounded-md border border-neutral-300 bg-transparent px-2 py-1 text-sm focus-visible:outline-2 focus-visible:outline-neutral-900 dark:border-neutral-700"
      >
        {routing.locales.map((l) => (
          <option key={l} value={l} lang={l}>
            {endonym(l)}
          </option>
        ))}
    </select>
  );
}
