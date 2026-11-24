import type { components } from "@geomaster/api-contract";
import { getPlan, type PlanId } from "@geomaster/plans";
import { getFormatter, getTranslations } from "next-intl/server";
import { Suspense } from "react";

import { Link } from "@/i18n/navigation";
import { coreFor } from "@/lib/core";
import { currentLocale } from "@/lib/locale";
import { isActiveScan, TOPICS, type Topic } from "@/lib/scan-errors";
import { requireSession } from "@/lib/session";

import { addPrompt, deletePrompt, startScan } from "./actions";
import { PromptForm } from "./prompt-form";
import { AutoRefresh, ScanButton } from "./scan-controls";

type Scan = components["schemas"]["ScanView"];
type Prompt = components["schemas"]["PromptResponse"];
type Lang = "en" | "de" | "fr" | "es";
type Engine = "openai" | "gemini" | "claude";
type ScanStatus = "queued" | "running" | "completed" | "partial" | "failed";

const muted = "text-sm text-neutral-600 dark:text-neutral-400";
const card = "rounded-lg border border-neutral-200 p-4 dark:border-neutral-800";

export default function ProjectPage({ params }: PageProps<"/[locale]/projects/[id]">) {
  return (
    <Suspense fallback={<ProjectLoading />}>
      <ProjectDetail params={params} />
    </Suspense>
  );
}

async function ProjectLoading() {
  const t = await getTranslations("Project");
  return <p role="status">{t("loading")}</p>;
}

/** Project page: prompts (REQ-1, REQ-2), "Scan now" (REQ-16) and scan progress per engine (REQ-17). */
async function ProjectDetail({ params }: { params: Promise<{ id: string }> }) {
  const { id } = await params;
  const locale = await currentLocale();
  const session = await requireSession(locale, `/projects/${id}`);
  const [t, tLang, tEngine, format] = await Promise.all([
    getTranslations("Project"),
    getTranslations("Languages"),
    getTranslations("Engines"),
    getFormatter(),
  ]);
  const core = await coreFor(session);
  const path = { params: { path: { projectId: id } } };
  const [project, prompts, scans, account] = await Promise.all([
    core.GET("/v1/projects/{id}", { params: { path: { id } } }),
    core.GET("/v1/projects/{projectId}/prompts", path),
    core.GET("/v1/projects/{projectId}/scans", path),
    core.GET("/v1/account"),
  ]);

  if (project.response.status === 404) {
    return (
      <div className="flex flex-col gap-4">
        <h1 className="text-2xl font-semibold">{t("notFound")}</h1>
        <BackLink label={t("back")} />
      </div>
    );
  }
  if (!project.data || !prompts.data || !scans.data) {
    return (
      <p role="alert" className="text-red-700 dark:text-red-400">
        {t("loadError")}
      </p>
    );
  }

  const p = project.data;
  const languages = (p.languages ?? []) as Lang[];
  const promptCap = getPlan((account.data?.planId ?? "free") as PlanId).prompts;
  const active = scans.data.some((s) => isActiveScan(s.status));
  const byTopic = (topic: Topic) => prompts.data!.filter((q) => q.topic === topic);

  return (
    <div className="flex flex-col gap-8">
      <AutoRefresh active={active} />
      <div className="flex flex-col gap-2">
        <BackLink label={t("back")} />
        <h1 className="text-2xl font-semibold">{p.domain}</h1>
        <p className={muted}>{languages.map((l) => tLang(l)).join(", ")}</p>
      </div>

      <section aria-labelledby="scans-heading" className="flex flex-col gap-4">
        <div className="flex flex-wrap items-start justify-between gap-4">
          <h2 id="scans-heading" className="text-xl font-semibold">
            {t("scansTitle")}
          </h2>
          <ScanButton action={startScan.bind(null, locale, id)} active={active} />
        </div>
        {scans.data.length === 0 ? (
          <p className={muted}>{t("scansEmpty")}</p>
        ) : (
          <ul className="grid gap-3">
            {scans.data.map((s: Scan) => (
              <li key={s.id} className={card}>
                <div className="flex flex-wrap items-baseline justify-between gap-2">
                  <span className="font-medium">
                    {s.createdAt ? format.dateTime(new Date(s.createdAt), { dateStyle: "medium", timeStyle: "short" }) : null}
                  </span>
                  <span className="rounded-full bg-neutral-100 px-2 py-0.5 text-xs dark:bg-neutral-800">
                    {t(`scanStatus.${(s.status ?? "queued") as ScanStatus}`)}
                  </span>
                </div>
                <p className={`mt-1 ${muted}`}>
                  {t("scanProgress", { done: s.unitsDone ?? 0, total: s.unitsTotal ?? 0, failed: s.unitsFailed ?? 0 })}
                  {s.costChf != null && ` · ${format.number(s.costChf, { style: "currency", currency: "CHF", maximumFractionDigits: 2 })}`}
                </p>
                <ul className="mt-3 grid gap-2">
                  {(s.engines ?? []).map((e) => {
                    const total = e.total ?? 0;
                    const finished = (e.done ?? 0) + (e.failed ?? 0);
                    const engine = tEngine((e.engine ?? "openai") as Engine);
                    return (
                      <li key={e.engine} className="grid gap-1 text-sm">
                        <div className="flex justify-between gap-2">
                          <span>{engine}</span>
                          <span className={muted}>
                            {t("engineProgress", { done: e.done ?? 0, total, failed: e.failed ?? 0 })}
                          </span>
                        </div>
                        <progress
                          value={finished}
                          max={Math.max(total, 1)}
                          aria-label={t("engineProgressLabel", { engine })}
                          className="h-2 w-full accent-neutral-900 dark:accent-white"
                        />
                      </li>
                    );
                  })}
                </ul>
              </li>
            ))}
          </ul>
        )}
      </section>

      <section aria-labelledby="prompts-heading" className="flex flex-col gap-4">
        <div className="flex flex-wrap items-baseline justify-between gap-2">
          <h2 id="prompts-heading" className="text-xl font-semibold">
            {t("promptsTitle")}
          </h2>
          {promptCap !== null && <p className={muted}>{t("promptsUsage", { used: prompts.data.length, allowed: promptCap })}</p>}
        </div>
        <PromptForm
          action={addPrompt.bind(null, locale, id)}
          languages={languages.map((l) => ({ code: l, name: tLang(l) }))}
          defaultLanguage={languages.includes(locale) ? locale : (languages[0] ?? locale)}
        />
        {prompts.data.length === 0 ? (
          <p className={muted}>{t("promptsEmpty")}</p>
        ) : (
          TOPICS.filter((topic) => byTopic(topic).length > 0).map((topic) => (
            <div key={topic} className="flex flex-col gap-2">
              <h3 className="font-medium">{t(`topics.${topic}`)}</h3>
              <ul className="grid gap-2">
                {byTopic(topic).map((q: Prompt) => (
                  <li key={q.id} className={`${card} flex items-start justify-between gap-4`}>
                    <div className="flex flex-col gap-1">
                      <span>{q.text}</span>
                      <span className={muted}>{tLang((q.locale ?? "en") as Lang)}</span>
                    </div>
                    <form action={deletePrompt.bind(null, locale, id, q.id!)}>
                      <button
                        type="submit"
                        aria-label={t("promptDeleteLabel", { text: q.text ?? "" })}
                        className="text-sm underline underline-offset-4 hover:text-red-700 dark:hover:text-red-400"
                      >
                        {t("promptDelete")}
                      </button>
                    </form>
                  </li>
                ))}
              </ul>
            </div>
          ))
        )}
      </section>
    </div>
  );
}

function BackLink({ label }: { label: string }) {
  return (
    <Link href="/projects" className="text-sm underline underline-offset-4">
      {label}
    </Link>
  );
}
