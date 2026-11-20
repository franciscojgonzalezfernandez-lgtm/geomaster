import { getPlan, type PlanId } from "@geomaster/plans";
import { getTranslations } from "next-intl/server";
import { Suspense } from "react";

import { coreFor } from "@/lib/core";
import { currentLocale } from "@/lib/locale";
import { requireSession } from "@/lib/session";

import { createProject } from "../actions";
import { ProjectForm } from "./project-form";

/** Markets offered first: CH/DACH focus, then ES and neighbours (PRD "Mercados"). Core accepts any ISO code. */
const COUNTRIES = ["CH", "DE", "AT", "LI", "FR", "IT", "ES", "BE", "NL", "LU", "GB", "IE", "PT", "US"];

export default async function NewProjectPage() {
  const t = await getTranslations("NewProject");
  return (
    <div className="flex flex-col gap-6">
      <div className="flex flex-col gap-2">
        <h1 className="text-2xl font-semibold">{t("title")}</h1>
        <p className="text-neutral-600 dark:text-neutral-400">{t("intro")}</p>
      </div>
      <Suspense fallback={null}>
        <NewProjectForm />
      </Suspense>
    </div>
  );
}

async function NewProjectForm() {
  const locale = await currentLocale();
  const session = await requireSession(locale, "/projects/new");
  const core = await coreFor(session);
  const { data: account } = await core.GET("/v1/account");
  // The plan's language cap comes from packages/plans; core enforces the same number (REQ-13).
  const maxLanguages = getPlan((account?.planId ?? "free") as PlanId).languages;
  const names = new Intl.DisplayNames([locale], { type: "region" });
  const countries = COUNTRIES.map((code) => ({ code, name: names.of(code) ?? code }));
  return (
    <ProjectForm
      action={createProject.bind(null, locale)}
      maxLanguages={maxLanguages}
      defaultLanguage={locale}
      countries={countries}
    />
  );
}
