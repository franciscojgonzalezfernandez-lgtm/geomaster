import "server-only";

import { createTranslator } from "next-intl";
import { Resend } from "resend";

import { loadMessages } from "@/i18n/messages";
import { isAppLocale, routing, type AppLocale } from "@/i18n/routing";

/**
 * The sign-in form sends `callbackURL=/<locale>/…`; Better Auth copies it into the magic link,
 * so the email's language follows the page the user signed in from.
 */
export function localeFromMagicLink(url: string): AppLocale {
  try {
    const callback = new URL(url).searchParams.get("callbackURL") ?? "";
    const segment = callback.split("/")[1];
    return isAppLocale(segment) ? segment : routing.defaultLocale;
  } catch {
    return routing.defaultLocale;
  }
}

const escapeHtml = (s: string) =>
  s.replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;").replace(/"/g, "&quot;");

/** Localized magic-link email (subject, HTML and plain text) in EN, DE, FR or ES (NFR-08). */
export async function magicLinkEmail(url: string, locale: AppLocale) {
  // Route handlers have no [locale] segment, so build the translator from the messages directly.
  const t = createTranslator({ locale, messages: await loadMessages(locale), namespace: "Email" });
  const href = escapeHtml(url);
  const html = `<!doctype html><html lang="${locale}"><body style="font-family:Arial,Helvetica,sans-serif;color:#171717;max-width:480px;margin:0 auto;padding:24px">
<h1 style="font-size:20px">${escapeHtml(t("magicLinkHeading"))}</h1>
<p>${escapeHtml(t("magicLinkBody"))}</p>
<p><a href="${href}" style="display:inline-block;background:#171717;color:#ffffff;padding:12px 20px;border-radius:6px;text-decoration:none">${escapeHtml(t("magicLinkButton"))}</a></p>
<p style="font-size:13px;color:#525252">${escapeHtml(t("magicLinkIgnore"))}</p>
</body></html>`;
  const text = `${t("magicLinkHeading")}\n\n${t("magicLinkBody")}\n\n${url}\n\n${t("magicLinkIgnore")}`;
  return { subject: t("magicLinkSubject"), html, text };
}

/**
 * Magic-link delivery (ADR-0014). With `EMAIL_TRANSPORT=log` (local dev and tests) the link is
 * written to the server log instead of being sent. Production requires RESEND_API_KEY.
 */
export async function sendMagicLinkEmail(email: string, url: string): Promise<void> {
  const locale = localeFromMagicLink(url);
  if (process.env.EMAIL_TRANSPORT === "log") {
    if (process.env.NODE_ENV === "production") {
      throw new Error("EMAIL_TRANSPORT=log is not allowed in production");
    }
    console.info(`[magic-link] ${email} (${locale}) ${url}`);
    return;
  }
  const apiKey = process.env.RESEND_API_KEY;
  if (!apiKey) {
    throw new Error("RESEND_API_KEY is not set");
  }
  const from = process.env.EMAIL_FROM ?? "GEOMASTER <onboarding@resend.dev>";
  const { subject, html, text } = await magicLinkEmail(url, locale);
  const { error } = await new Resend(apiKey).emails.send({ from, to: email, subject, html, text });
  if (error) {
    throw new Error(`Resend rejected the magic-link email: ${error.name}`);
  }
}
