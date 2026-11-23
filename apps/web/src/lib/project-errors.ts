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
export function problemToError(problem: unknown): Pick<CreateProjectState, "error" | "allowed"> {
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
