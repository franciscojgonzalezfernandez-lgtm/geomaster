import { plans } from "@geomaster/plans";

export default function Home() {
  const selfServe = plans.filter((plan) => plan.selfServe);
  return (
    <main className="mx-auto flex min-h-screen max-w-2xl flex-col justify-center gap-6 p-8">
      <h1 className="text-4xl font-semibold">GEOMASTER</h1>
      <p className="text-lg">
        See how ChatGPT, Gemini and Claude talk about your brand, in English, German, French and Spanish.
      </p>
      <p className="text-sm opacity-70">
        Closed beta from December 2026 · {selfServe.length} self-serve plans
      </p>
    </main>
  );
}
