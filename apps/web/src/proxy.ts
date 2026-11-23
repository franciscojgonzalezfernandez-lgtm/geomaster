import createMiddleware from "next-intl/middleware";

import { routing } from "./i18n/routing";

/** Locale prefix routing: `/` → `/<cookie or Accept-Language locale>`, falling back to EN (REQ-16). */
export default createMiddleware(routing);

export const config = {
  // Skip the auth API, Next internals and files with an extension (favicon, robots.txt, …).
  matcher: ["/((?!api|_next|_vercel|.*\\..*).*)"],
};
