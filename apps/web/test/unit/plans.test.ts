import { getPlan, plans } from "@geomaster/plans";
import data from "@geomaster/plans/plans.json";
import { describe, expect, it } from "vitest";

describe("plan limits (FR-BIL-03: single source of truth)", () => {
  it("exposes every plan from plans.json unchanged", () => {
    expect(plans).toEqual(data.plans);
  });

  it("matches the PRD limits the UI relies on", () => {
    expect(getPlan("free")).toMatchObject({ domains: 1, languages: 1, engines: 1, prompts: 12 });
    expect(getPlan("starter")).toMatchObject({ domains: 1, languages: 2, engines: 3, prompts: 30 });
    expect(getPlan("pro")).toMatchObject({ domains: 3, languages: 4, prompts: 100 });
    expect(getPlan("premium")).toMatchObject({ domains: 5, languages: 4, prompts: 250 });
  });

  it("allows at most the four interface/measurement languages", () => {
    for (const plan of plans) expect(plan.languages).toBeLessThanOrEqual(4);
  });

  it("throws for an unknown plan", () => {
    // @ts-expect-error -- deliberately invalid id
    expect(() => getPlan("gold")).toThrow(/Unknown plan/);
  });
});
