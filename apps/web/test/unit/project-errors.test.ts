import { describe, expect, it } from "vitest";

import { problemToError } from "@/lib/project-errors";

describe("core 422 → form error (REQ-13)", () => {
  it("maps plan limits with the allowed count", () => {
    expect(problemToError({ status: 422, limit: "languages", allowed: 2 })).toEqual({ error: "languagesLimit", allowed: 2 });
    expect(problemToError({ status: 422, limit: "domains", allowed: 1 })).toEqual({ error: "domainsLimit", allowed: 1 });
  });

  it("reads Spring's nested `properties` too", () => {
    expect(problemToError({ properties: { field: "country" } })).toEqual({ error: "country" });
  });

  it("maps invalid fields", () => {
    for (const field of ["domain", "languages", "country", "region"]) {
      expect(problemToError({ field })).toEqual({ error: field });
    }
  });

  it("falls back to a generic error", () => {
    expect(problemToError(undefined)).toEqual({ error: "generic" });
    expect(problemToError({ field: "secret" })).toEqual({ error: "generic" });
    expect(problemToError("oops")).toEqual({ error: "generic" });
  });
});
