import { createTranslator } from "next-intl";
import { describe, expect, it } from "vitest";

import { loadMessages } from "@/i18n/messages";
import { routing } from "@/i18n/routing";

type Tree = { [key: string]: string | Tree };

const keys = (tree: Tree, prefix = ""): string[] =>
  Object.entries(tree).flatMap(([k, v]) => (typeof v === "string" ? [prefix + k] : keys(v, `${prefix}${k}.`)));

// Sample values for every ICU argument used in the messages.
const ARGS = { count: 2, used: 1, allowed: 2, max: 1, plan: "Pro", email: "a@b.ch", date: "1.1.2027", done: 3, total: 12, failed: 1, engine: "ChatGPT", text: "Best school?" };

describe("UI strings (NFR-08: EN, DE, FR, ES)", () => {
  it("has identical keys in every locale", async () => {
    const reference = keys((await loadMessages("en")) as unknown as Tree).sort();
    for (const locale of routing.locales) {
      expect(keys((await loadMessages(locale)) as unknown as Tree).sort(), locale).toEqual(reference);
    }
  });

  it("formats every message without ICU errors or empty strings", async () => {
    for (const locale of routing.locales) {
      const messages = await loadMessages(locale);
      const errors: string[] = [];
      const t = createTranslator({ locale, messages, onError: (e) => errors.push(e.message) });
      for (const key of keys(messages as unknown as Tree)) {
        // @ts-expect-error -- keys are computed at runtime from the same messages
        const out: string = t(key, ARGS);
        expect(out.trim(), `${locale}:${key}`).not.toBe("");
      }
      expect(errors, locale).toEqual([]);
    }
  });
});
