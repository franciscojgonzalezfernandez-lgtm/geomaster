import "server-only";

import { Resend } from "resend";

/**
 * Magic-link delivery (ADR-0014). With `EMAIL_TRANSPORT=log` (local dev and tests) the link is
 * written to the server log instead of being sent. Production requires RESEND_API_KEY.
 */
export async function sendMagicLinkEmail(email: string, url: string): Promise<void> {
  if (process.env.EMAIL_TRANSPORT === "log") {
    if (process.env.NODE_ENV === "production") {
      throw new Error("EMAIL_TRANSPORT=log is not allowed in production");
    }
    console.info(`[magic-link] ${email} ${url}`);
    return;
  }
  const apiKey = process.env.RESEND_API_KEY;
  if (!apiKey) {
    throw new Error("RESEND_API_KEY is not set");
  }
  const from = process.env.EMAIL_FROM ?? "GEOMASTER <onboarding@resend.dev>";
  // Subject and body move to localized React Email templates with the sign-in UI (T10).
  const { error } = await new Resend(apiKey).emails.send({
    from,
    to: email,
    subject: "Your GEOMASTER sign-in link",
    text: `Sign in to GEOMASTER: ${url}\n\nThe link expires in 15 minutes. If you did not ask for it, ignore this email.`,
  });
  if (error) {
    throw new Error(`Resend rejected the magic-link email: ${error.name}`);
  }
}
