"use client";

import { useTranslations } from "next-intl";
import { useRouter } from "next/navigation";
import { useActionState, useEffect } from "react";

import type { StartScanState } from "@/lib/scan-errors";

const primaryButton =
  "rounded-md bg-neutral-900 px-4 py-2 text-sm font-medium text-white hover:bg-neutral-700 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-neutral-900 disabled:opacity-60 dark:bg-white dark:text-neutral-900 dark:hover:bg-neutral-200";

/** "Scan now" (REQ-16). Disabled while a scan is in progress; refusals are shown as an alert. */
export function ScanButton({ action, active }: { action: (prev: StartScanState) => Promise<StartScanState>; active: boolean }) {
  const t = useTranslations("Project");
  const [state, formAction, pending] = useActionState(action, {});
  return (
    <form action={formAction} className="flex flex-col items-start gap-2">
      <button type="submit" disabled={pending || active} className={primaryButton}>
        {pending ? t("scanStarting") : t("scanNow")}
      </button>
      {state.error && (
        <p id="scan-error" role="alert" className="text-sm text-red-700 dark:text-red-400">
          {t(`refusals.${state.error}`)}
        </p>
      )}
    </form>
  );
}

/** Re-renders the server page every few seconds while a scan is queued or running (REQ-17 progress). */
export function AutoRefresh({ active, intervalMs = 3000 }: { active: boolean; intervalMs?: number }) {
  const router = useRouter();
  useEffect(() => {
    if (!active) return;
    const id = setInterval(() => router.refresh(), intervalMs);
    return () => clearInterval(id);
  }, [active, intervalMs, router]);
  return null;
}
