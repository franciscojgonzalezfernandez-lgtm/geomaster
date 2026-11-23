"use server";

import { redirect } from "@/i18n/navigation";
import { isAppLocale, routing } from "@/i18n/routing";
import { coreFor } from "@/lib/core";
import { getSession } from "@/lib/session";

export type CreateProjectError =
  | "domain"
  | "languages"
  | "languagesLimit"
  | "country"
  | "region"
  | "domainsLimit"
  | "generic";

export type CreateProjectState = {
  error?: CreateProjectError;
  allowed?: number;
  values?: { domain: string; languages: string[]; country: string; region: string };
};

/** Core's 422 ProblemDetail: `field` for invalid input, `limit` + `allowed` for plan limits (REQ-13). */
function toError(problem: unknown): Pick<CreateProjectState, "error" | "allowed"> {
  const p = (problem ?? {}) as Record<string, unknown>;
  const props = { ...((p.properties as Record<string, unknown> | undefined) ?? {}), ...p };
  if (props.limit === "languages") return { error: "languagesLimit", allowed: Number(props.allowed) };
  if (props.limit === "domains") return { error: "domainsLimit", allowed: Number(props.allowed) };
  const field = props.field;
  if (field === "domain" || field === "languages" || field === "country" || field === "region") {
    return { error: field };
  }
  return { error: "generic" };
}

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
  if (!data) return { ...toError(error), values };
  redirect({ href: "/projects", locale: l });
  return {};
}
