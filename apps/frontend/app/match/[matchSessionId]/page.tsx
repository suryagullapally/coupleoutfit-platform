import type { Metadata } from "next";
import { PageContainer } from "@/components/page-container";
import { PublicHeader } from "@/components/public-header";
import { SiteFooter } from "@/components/site-footer";
import { MatchSessionExperience } from "@/features/matching/match-session-experience";

export const metadata: Metadata = {
  title: "Your partner matches",
  description: "View the status of your CoupleOutfit partner match.",
  robots: { index: false, follow: true },
};

export default async function MatchSessionPage({ params }: { params: Promise<{ matchSessionId: string }> }) {
  const candidate = (await params).matchSessionId;
  const matchSessionId = /^[a-f0-9]{8}-[a-f0-9]{4}-[1-5][a-f0-9]{3}-[89ab][a-f0-9]{3}-[a-f0-9]{12}$/i.test(candidate) ? candidate : null;
  return (
    <div className="min-h-svh">
      <PublicHeader />
      <main id="main-content" tabIndex={-1} className="py-10 sm:py-16">
        <PageContainer><MatchSessionExperience matchSessionId={matchSessionId} /></PageContainer>
      </main>
      <SiteFooter />
    </div>
  );
}
