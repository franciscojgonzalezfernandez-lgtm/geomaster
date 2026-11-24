import { describe, expect, it } from "vitest";

import { isActiveScan, promptProblem, scanRefusal, SCAN_REFUSALS } from "@/lib/scan-errors";

describe("core 409 → scan refusal (scan-engine REQ-16)", () => {
  it("maps every reason core sends", () => {
    for (const reason of SCAN_REFUSALS) {
      expect(scanRefusal({ status: 409, reason })).toBe(reason);
    }
  });

  it("reads Spring's nested `properties` too", () => {
    expect(scanRefusal({ properties: { reason: "no_prompts" } })).toBe("no_prompts");
  });

  it("falls back to generic", () => {
    expect(scanRefusal(undefined)).toBe("generic");
    expect(scanRefusal({ reason: "something_new" })).toBe("generic");
    expect(scanRefusal("oops")).toBe("generic");
  });
});

describe("core 422 → prompt form error (REQ-1)", () => {
  it("maps the plan's prompt cap with the allowed count", () => {
    expect(promptProblem({ limit: "prompts", allowed: 12 })).toEqual({ error: "promptsLimit", allowed: 12 });
  });

  it("maps invalid fields and falls back to generic", () => {
    for (const field of ["text", "locale", "topic"]) {
      expect(promptProblem({ field })).toEqual({ error: field });
    }
    expect(promptProblem({ field: "secret" })).toEqual({ error: "generic" });
  });
});

describe("active scans drive auto-refresh (REQ-17)", () => {
  it("treats queued and running as active", () => {
    expect(isActiveScan("queued")).toBe(true);
    expect(isActiveScan("running")).toBe(true);
    for (const s of ["completed", "partial", "failed", undefined]) {
      expect(isActiveScan(s)).toBe(false);
    }
  });
});
