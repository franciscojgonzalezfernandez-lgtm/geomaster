/** Why core refused to start a scan (409 `reason`, scan-engine REQ-16). */
export const SCAN_REFUSALS = ["already_active", "frequency", "budget", "project_inactive", "no_prompts", "no_engines"] as const;
export type ScanRefusal = (typeof SCAN_REFUSALS)[number] | "generic";

export type StartScanState = { error?: ScanRefusal };

export type PromptError = "text" | "locale" | "topic" | "promptsLimit" | "generic";
export type AddPromptState = {
  error?: PromptError;
  allowed?: number;
  values?: { text: string; locale: string; topic: string };
};

/** The six intent topics of FR-ONB-03, in display order; core validates the same set. */
export const TOPICS = ["reviews", "pricing", "use_cases", "alternatives", "how_to", "comparison"] as const;
export type Topic = (typeof TOPICS)[number];

/** Scan states core reports; queued and running are still in progress. */
export const ACTIVE_SCAN_STATUSES = ["queued", "running"] as const;

function props(problem: unknown): Record<string, unknown> {
  const p = (problem && typeof problem === "object" ? problem : {}) as Record<string, unknown>;
  return { ...((p.properties as Record<string, unknown> | undefined) ?? {}), ...p };
}

/** Core's 409 ProblemDetail carries `reason`; anything unknown is generic. */
export function scanRefusal(problem: unknown): ScanRefusal {
  const reason = props(problem).reason;
  return (SCAN_REFUSALS as readonly unknown[]).includes(reason) ? (reason as ScanRefusal) : "generic";
}

/** Core's 422 ProblemDetail for prompts: `field` for invalid input, `limit` + `allowed` for the plan cap (REQ-1). */
export function promptProblem(problem: unknown): Pick<AddPromptState, "error" | "allowed"> {
  const p = props(problem);
  if (p.limit === "prompts") return { error: "promptsLimit", allowed: Number(p.allowed) };
  if (p.field === "text" || p.field === "locale" || p.field === "topic") return { error: p.field };
  return { error: "generic" };
}

export function isActiveScan(status: string | undefined): boolean {
  return (ACTIVE_SCAN_STATUSES as readonly (string | undefined)[]).includes(status);
}
