import type { Metadata } from "next";

import { AppHeader } from "@/components/app-header";

/** Signed-in pages are private: keep them out of search indexes. */
export const metadata: Metadata = { robots: { index: false, follow: false } };

export default function ProjectsLayout({ children }: LayoutProps<"/[locale]/projects">) {
  return (
    <>
      <AppHeader />
      <main id="main" className="mx-auto w-full max-w-4xl flex-1 p-4 sm:p-6">
        {children}
      </main>
    </>
  );
}
