"use server";

import { refresh } from "next/cache";

import { redirect } from "@/i18n/navigation";
import { isAppLocale, routing } from "@/i18n/routing";
import { coreFor } from "@/lib/core";
import { promptProblem, scanRefusal, type AddPromptState, type StartScanState } from "@/lib/scan-errors";
import { getSession } from "@/lib/session";

async function coreOrSignIn(locale: string) {
  const session = await getSession();
  if (!session) redirect({ href: "/signin", locale: isAppLocale(locale) ? locale : routing.defaultLocale });
  return coreFor(session!);
}

/** Add a hand-written prompt (scan-engine REQ-1): core checks the language, topic and the plan's prompt cap. */
export async function addPrompt(
  locale: string,
  projectId: string,
  _prev: AddPromptState,
  form: FormData,
): Promise<AddPromptState> {
  const values = {
    text: String(form.get("text") ?? "").trim(),
    locale: String(form.get("locale") ?? ""),
    topic: String(form.get("topic") ?? ""),
  };
  if (!values.text) return { error: "text", values };
  const core = await coreOrSignIn(locale);
  const { data, error } = await core.POST("/v1/projects/{projectId}/prompts", {
    params: { path: { projectId } },
    body: values,
  });
  if (!data) return { ...promptProblem(error), values };
  refresh();
  return {};
}

/** Delete a prompt (REQ-2): it is not asked again; past answers keep their version. */
export async function deletePrompt(locale: string, projectId: string, promptId: string): Promise<void> {
  const core = await coreOrSignIn(locale);
  await core.DELETE("/v1/projects/{projectId}/prompts/{promptId}", {
    params: { path: { projectId, promptId } },
  });
  refresh();
}

/** Start a scan now (REQ-16); core's 409 `reason` becomes a localized message. */
// eslint-disable-next-line @typescript-eslint/no-unused-vars -- useActionState passes the previous state first
export async function startScan(locale: string, projectId: string, _prev: StartScanState): Promise<StartScanState> {
  const core = await coreOrSignIn(locale);
  const { data, error } = await core.POST("/v1/projects/{projectId}/scans", {
    params: { path: { projectId } },
  });
  if (!data) return { error: scanRefusal(error) };
  refresh();
  return {};
}
