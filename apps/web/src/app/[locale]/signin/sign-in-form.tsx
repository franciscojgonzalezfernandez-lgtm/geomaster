"use client";

import { useLocale, useTranslations } from "next-intl";
import { useId, useState, useTransition } from "react";

import { authClient } from "@/lib/auth-client";

const button =
  "w-full rounded-md px-4 py-2 font-medium focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-neutral-900 disabled:opacity-60";

export function SignInForm({ googleEnabled }: { googleEnabled: boolean }) {
  const t = useTranslations("SignIn");
  const locale = useLocale();
  const emailId = useId();
  const [email, setEmail] = useState("");
  const [sentTo, setSentTo] = useState<string | null>(null);
  const [error, setError] = useState(false);
  const [pending, startTransition] = useTransition();
  // The locale prefix makes the magic-link email and the landing page use this language.
  const callbackURL = `/${locale}/projects`;

  function sendLink(e: React.FormEvent<HTMLFormElement>) {
    e.preventDefault();
    setError(false);
    startTransition(async () => {
      const { error } = await authClient.signIn.magicLink({ email, callbackURL });
      if (error) setError(true);
      else setSentTo(email);
    });
  }

  if (sentTo) {
    return (
      <div role="status" className="flex flex-col gap-3">
        <h2 className="text-xl font-semibold">{t("checkTitle")}</h2>
        <p>{t("checkBody", { email: sentTo })}</p>
        <button type="button" onClick={() => setSentTo(null)} className="self-start text-sm underline underline-offset-4">
          {t("useDifferent")}
        </button>
      </div>
    );
  }

  return (
    <div className="flex flex-col gap-6">
      <form onSubmit={sendLink} className="flex flex-col gap-3">
        <label htmlFor={emailId} className="text-sm font-medium">
          {t("emailLabel")}
        </label>
        <input
          id={emailId}
          type="email"
          name="email"
          autoComplete="email"
          required
          value={email}
          onChange={(e) => setEmail(e.target.value)}
          aria-invalid={error || undefined}
          aria-describedby={error ? `${emailId}-error` : undefined}
          className="rounded-md border border-neutral-300 bg-transparent px-3 py-2 focus-visible:outline-2 focus-visible:outline-neutral-900 dark:border-neutral-700"
        />
        {error && (
          <p id={`${emailId}-error`} role="alert" className="text-sm text-red-700 dark:text-red-400">
            {t("error")}
          </p>
        )}
        <button
          type="submit"
          disabled={pending}
          className={`${button} bg-neutral-900 text-white hover:bg-neutral-700 dark:bg-white dark:text-neutral-900 dark:hover:bg-neutral-200`}
        >
          {pending ? t("sending") : t("submit")}
        </button>
      </form>
      {googleEnabled && (
        <>
          <div className="flex items-center gap-3 text-sm text-neutral-600 dark:text-neutral-400" aria-hidden="true">
            <span className="h-px flex-1 bg-neutral-300 dark:bg-neutral-700" />
            {t("or")}
            <span className="h-px flex-1 bg-neutral-300 dark:bg-neutral-700" />
          </div>
          <button
            type="button"
            disabled={pending}
            onClick={() => startTransition(async () => void (await authClient.signIn.social({ provider: "google", callbackURL })))}
            className={`${button} border border-neutral-300 hover:bg-neutral-100 dark:border-neutral-700 dark:hover:bg-neutral-900`}
          >
            {t("google")}
          </button>
        </>
      )}
    </div>
  );
}
