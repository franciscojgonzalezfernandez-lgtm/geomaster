"use client";

import { useTranslations } from "next-intl";
import { useActionState, useState } from "react";

import { Link } from "@/i18n/navigation";
import { routing, type AppLocale } from "@/i18n/routing";

import type { CreateProjectState } from "@/lib/project-errors";

type Props = {
  action: (prev: CreateProjectState, form: FormData) => Promise<CreateProjectState>;
  maxLanguages: number;
  defaultLanguage: AppLocale;
  countries: { code: string; name: string }[];
};

const field =
  "rounded-md border border-neutral-300 bg-transparent px-3 py-2 focus-visible:outline-2 focus-visible:outline-neutral-900 dark:border-neutral-700";
const hint = "text-sm text-neutral-600 dark:text-neutral-400";

export function ProjectForm({ action, maxLanguages, defaultLanguage, countries }: Props) {
  const t = useTranslations("NewProject");
  const tLang = useTranslations("Languages");
  const [state, formAction, pending] = useActionState(action, {});
  const [languages, setLanguages] = useState<string[]>(state.values?.languages ?? [defaultLanguage]);
  const single = maxLanguages <= 1;
  const errorFor = (...keys: string[]) => (state.error && keys.includes(state.error) ? state.error : null);
  const message = state.error
    ? t(`errors.${state.error}`, { allowed: state.allowed ?? maxLanguages })
    : null;

  function toggle(l: string, checked: boolean) {
    setLanguages((cur) => (checked ? [...cur, l] : cur.filter((x) => x !== l)));
  }

  return (
    <form action={formAction} className="flex max-w-lg flex-col gap-6" aria-describedby={message ? "form-error" : undefined}>
      {message && (
        <p id="form-error" role="alert" className="rounded-md bg-red-50 p-3 text-sm text-red-800 dark:bg-red-950 dark:text-red-200">
          {message}
        </p>
      )}

      <div className="flex flex-col gap-1">
        <label htmlFor="domain" className="font-medium">
          {t("domain")}
        </label>
        <input
          id="domain"
          name="domain"
          required
          inputMode="url"
          autoComplete="url"
          defaultValue={state.values?.domain}
          aria-invalid={errorFor("domain", "domainsLimit") ? true : undefined}
          aria-describedby="domain-hint"
          className={field}
        />
        <p id="domain-hint" className={hint}>
          {t("domainHint")}
        </p>
      </div>

      <fieldset className="flex flex-col gap-2" aria-describedby="languages-hint">
        <legend className="font-medium">{t("languages")}</legend>
        <p id="languages-hint" className={hint}>
          {t("languagesHint", { max: maxLanguages })}
        </p>
        <div className="flex flex-wrap gap-4">
          {routing.locales.map((l) => {
            const checked = languages.includes(l);
            return (
              <label key={l} className="flex items-center gap-2">
                {/* One language allowed: a radio group, so picking another moves the choice. */}
                <input
                  type={single ? "radio" : "checkbox"}
                  name="languages"
                  value={l}
                  checked={checked}
                  disabled={!single && !checked && languages.length >= maxLanguages}
                  onChange={(e) => (single ? setLanguages([l]) : toggle(l, e.target.checked))}
                  className="size-4"
                />
                {tLang(l)}
              </label>
            );
          })}
        </div>
      </fieldset>

      <div className="flex flex-col gap-1">
        <label htmlFor="country" className="font-medium">
          {t("country")}
        </label>
        <select
          id="country"
          name="country"
          required
          defaultValue={state.values?.country ?? "CH"}
          aria-invalid={errorFor("country") ? true : undefined}
          className={field}
        >
          <option value="" disabled>
            {t("countryPlaceholder")}
          </option>
          {countries.map((c) => (
            <option key={c.code} value={c.code}>
              {c.name}
            </option>
          ))}
        </select>
      </div>

      <div className="flex flex-col gap-1">
        <label htmlFor="region" className="font-medium">
          {t("region")}
        </label>
        <input
          id="region"
          name="region"
          maxLength={100}
          defaultValue={state.values?.region}
          aria-invalid={errorFor("region") ? true : undefined}
          aria-describedby="region-hint"
          className={field}
        />
        <p id="region-hint" className={hint}>
          {t("regionHint")}
        </p>
      </div>

      <div className="flex items-center gap-4">
        <button
          type="submit"
          disabled={pending}
          className="rounded-md bg-neutral-900 px-4 py-2 font-medium text-white hover:bg-neutral-700 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-neutral-900 disabled:opacity-60 dark:bg-white dark:text-neutral-900 dark:hover:bg-neutral-200"
        >
          {pending ? t("submitting") : t("submit")}
        </button>
        <Link href="/projects" className="text-sm underline underline-offset-4">
          {t("cancel")}
        </Link>
      </div>
    </form>
  );
}
