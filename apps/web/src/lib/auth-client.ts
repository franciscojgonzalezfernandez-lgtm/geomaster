import { createAuthClient } from "better-auth/react";
import { magicLinkClient } from "better-auth/client/plugins";

/** Browser-side Better Auth client used by the sign-in UI (T10). */
export const authClient = createAuthClient({
  plugins: [magicLinkClient()],
});
