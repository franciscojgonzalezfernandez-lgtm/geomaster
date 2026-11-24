"use client";

import { useTranslations } from "next-intl";
import { useActionState } from "react";

import { TOPICS, type AddPromptState } from "@/lib/scan-errors";

type Props = {
  action: (prev: AddPromptState, form: FormData) => Promise<AddPromptState>;
  languages: { code: string; name: string }[];
  defaultLanguage: string;
};

const field =
  "rounded-md border border-neutral-300 bg-transparent px-3 py-2 focus-visible:outline-2 focus-visible:outline-neutral-900 dark:border-neutral-700";

/** Add a prompt in one of the project's languages under one of the six topics (REQ-1). */
export function PromptForm({ action, languages, defaultLanguage }: Props) {
  const t = useTranslations("Project");
  const [state, formAction, pending] = useActionState(action, {});
  const message = state.error ? t(`promptErrors.${state.error}`, { allowed: state.allowed ?? 0 }) : null;

  return (
    <form
      action={formAction}
      className="flex flex-col gap-3 rounded-lg border border-neutral-200 p-4 dark:border-neutral-800"
      aria-describedby={message ? "prompt-error" : undefined}
    >
      {message && (
        <p id="prompt-error" role="alert" className="rounded-md bg-red-50 p-3 text-sm text-red-800 dark:bg-red-950 dark:text-red-200">
          {message}
        </p>
      )}
      <div className="flex flex-col gap-1">
        <label htmlFor="prompt-text" className="font-medium">
          {t("promptText")}
        </label>
        <textarea
          id="prompt-text"
          name="text"
          required
          rows={2}
          maxLength={2000}
          defaultValue={state.values?.text}
          aria-invalid={state.error === "text" ? true : undefined}
          className={field}
        />
      </div>
      <div className="flex flex-wrap gap-4">
        <div className="flex flex-col gap-1">
          <label htmlFor="prompt-locale" className="text-sm font-medium">
            {t("promptLanguage")}
          </label>
          <select id="prompt-locale" name="locale" defaultValue={state.values?.locale ?? defaultLanguage} className={field}>
            {languages.map((l) => (
              <option key={l.code} value={l.code}>
                {l.name}
              </option>
            ))}
          </select>
        </div>
        <div className="flex flex-col gap-1">
          <label htmlFor="prompt-topic" className="text-sm font-medium">
            {t("promptTopic")}
          </label>
          <select id="prompt-topic" name="topic" defaultValue={state.values?.topic ?? TOPICS[0]} className={field}>
            {TOPICS.map((topic) => (
              <option key={topic} value={topic}>
                {t(`topics.${topic}`)}
              </option>
            ))}
          </select>
        </div>
      </div>
      <div>
        <button
          type="submit"
          disabled={pending}
          className="rounded-md border border-neutral-900 px-4 py-2 text-sm font-medium hover:bg-neutral-100 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-neutral-900 disabled:opacity-60 dark:border-white dark:hover:bg-neutral-800"
        >
          {pending ? t("promptAdding") : t("promptAdd")}
        </button>
      </div>
    </form>
  );
}
