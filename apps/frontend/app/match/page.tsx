import type { Metadata } from "next";
import { PageContainer } from "@/components/page-container";
import { PublicHeader } from "@/components/public-header";
import { SiteFooter } from "@/components/site-footer";
import { AnalysisExperience } from "@/features/analysis/analysis-experience";

export const metadata: Metadata = {
  title: "Review your outfit",
  description: "Review the clothing details detected for your source outfit.",
  robots: { index: false, follow: true },
};

export default async function MatchPage({ searchParams }: { searchParams: Promise<{ source?: string | string[] }> }) {
  const source = (await searchParams).source;
  const sourceItemId = typeof source === "string" && /^[a-f0-9]{8}-[a-f0-9]{4}-[1-5][a-f0-9]{3}-[89ab][a-f0-9]{3}-[a-f0-9]{12}$/i.test(source) ? source : null;
  return (
    <div className="min-h-svh">
      <PublicHeader />
      <main id="main-content" tabIndex={-1} className="py-10 sm:py-16">
        <PageContainer><AnalysisExperience sourceItemId={sourceItemId} /></PageContainer>
      </main>
      <SiteFooter />
    </div>
  );
}
