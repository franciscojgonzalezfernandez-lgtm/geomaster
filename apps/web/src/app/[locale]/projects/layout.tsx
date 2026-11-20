import { AppHeader } from "@/components/app-header";

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
