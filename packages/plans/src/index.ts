import data from "../plans.json";

export type PlanId = "free" | "starter" | "pro" | "premium" | "enterprise";

/** `null` means "custom / unlimited" (enterprise or no cap). */
export interface Plan {
  id: PlanId;
  selfServe: boolean;
  priceChfMonthly: number | null;
  domains: number | null;
  prompts: number | null;
  engines: number;
  languages: number;
  scanFrequency: "once" | "weekly";
  competitorsPerDomain: number | null;
  artifactsPerMonth: number | null;
  seats: number | null;
  historyMonths: number | null;
  consultingMinutesPerMonth: number | null;
}

export const plans = data.plans as Plan[];

export function getPlan(id: PlanId): Plan {
  const plan = plans.find((p) => p.id === id);
  if (!plan) throw new Error(`Unknown plan: ${id}`);
  return plan;
}
