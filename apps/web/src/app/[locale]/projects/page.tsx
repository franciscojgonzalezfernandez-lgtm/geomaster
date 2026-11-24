import type { components } from "@geomaster/api-contract";
import { getPlan, type PlanId } from "@geomaster/plans";
import { getFormatter, getTranslations } from "next-intl/server";
import { Suspense } from "react";

import { Link } from "@/i18n/navigation";
import { coreFor } from "@/lib/core";
import { currentLocale } from "@/lib/locale";
import { requireSession } from "@/lib/session";

type Project = components["schemas"]["ProjectResponse"];
type Status = "active" | "paused" | "archived";

const primaryButton =
  "rounded-md bg-neutral-900 px-4 py-2 text-sm font-medium text-white hover:bg-neutral-700 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-neutral-900 dark:bg-white dark:text-neutral-900 dark:hover:bg-neutral-200";

export default async function ProjectsPage() {
  const t = await getTranslations("Projects");
  return (
    <div className="flex flex-col gap-6">
      <div className="flex flex-wrap items-center justify-between gap-4">
        <h1 className="text-2xl font-semibold">{t("title")}</h1>
        <Link href="/projects/new" className={primaryButton}>
          {t("new")}
        </Link>
      </div>
      <Suspense fallback={<p role="status">{t("loading")}</p>}>
        <ProjectList />
      </Suspense>
    </div>
  );
}

async function ProjectList() {
  const locale = await currentLocale();
  const session = await requireSession(locale, "/projects");
  const [t, tLang, tPlan, format] = await Promise.all([
    getTranslations("Projects"),
    getTranslations("Languages"),
    getTranslations("Plans"),
    getFormatter(),
  ]);
  const core = await coreFor(session);
  const [projects, account] = await Promise.all([core.GET("/v1/projects"), core.GET("/v1/account")]);
  if (!projects.data || !account.data) {
    return (
      <p role="alert" className="text-red-700 dark:text-red-400">
        {t("loadError")}
      </p>
    );
  }
  const planId = (account.data.planId ?? "free") as PlanId;
  const allowed = getPlan(planId).domains;
  const active = projects.data.filter((p) => p.status !== "archived").length;
  const countries = new Intl.DisplayNames([locale], { type: "region" });

  return (
    <div className="flex flex-col gap-4">
      {allowed !== null && (
        <p className="text-sm text-neutral-600 dark:text-neutral-400">
          {t("planUsage", { used: active, allowed, plan: tPlan(planId) })}
        </p>
      )}
      {projects.data.length === 0 ? (
        <p>{t("empty")}</p>
      ) : (
        <ul className="grid gap-3">
          {projects.data.map((p: Project) => (
            <li key={p.id} className="rounded-lg border border-neutral-200 p-4 dark:border-neutral-800">
              <div className="flex flex-wrap items-baseline justify-between gap-2">
                <h2 className="text-lg font-medium">
                  <Link href={`/projects/${p.id}`} className="underline-offset-4 hover:underline">
                    {p.domain}
                  </Link>
                </h2>
                <span className="rounded-full bg-neutral-100 px-2 py-0.5 text-xs dark:bg-neutral-800">
                  {t(`status.${(p.status ?? "active") as Status}`)}
                </span>
              </div>
              <dl className="mt-2 grid gap-1 text-sm sm:grid-cols-[auto_1fr] sm:gap-x-4">
                <dt className="text-neutral-600 dark:text-neutral-400">{t("languages")}</dt>
                <dd>{(p.languages ?? []).map((l) => tLang(l as "en" | "de" | "fr" | "es")).join(", ")}</dd>
                <dt className="text-neutral-600 dark:text-neutral-400">{t("market")}</dt>
                <dd>{[p.region, p.country ? countries.of(p.country) : null].filter(Boolean).join(", ")}</dd>
              </dl>
              {p.createdAt && (
                <p className="mt-2 text-xs text-neutral-600 dark:text-neutral-400">
                  {t("created", { date: format.dateTime(new Date(p.createdAt), { dateStyle: "medium" }) })}
                </p>
              )}
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}
