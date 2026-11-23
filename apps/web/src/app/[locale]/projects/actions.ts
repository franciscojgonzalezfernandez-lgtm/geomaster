"use server";

import { redirect } from "@/i18n/navigation";
import { isAppLocale, routing } from "@/i18n/routing";
import { coreFor } from "@/lib/core";
import { problemToError, type CreateProjectState } from "@/lib/project-errors";
import { getSession } from "@/lib/session";

/** Create-project form (REQ-10..13): core validates and enforces plan limits; we map its errors to UI strings. */
export async function createProject(
  locale: string,
  _prev: CreateProjectState,
  form: FormData,
): Promise<CreateProjectState> {
  const l = isAppLocale(locale) ? locale : routing.defaultLocale;
  const values = {
    domain: String(form.get("domain") ?? "").trim(),
    languages: form.getAll("languages").map(String),
    country: String(form.get("country") ?? ""),
    region: String(form.get("region") ?? "").trim(),
  };
  if (!values.domain) return { error: "domain", values };
  if (values.languages.length === 0) return { error: "languages", values };
  if (!values.country) return { error: "country", values };

  const session = await getSession();
  if (!session) redirect({ href: "/signin", locale: l });
  const core = await coreFor(session!);
  const { data, error } = await core.POST("/v1/projects", {
    body: {
      domain: values.domain,
      languages: values.languages,
      country: values.country,
      region: values.region || undefined,
    },
  });
  if (!data) return { ...problemToError(error), values };
  redirect({ href: "/projects", locale: l });
  return {};
}
