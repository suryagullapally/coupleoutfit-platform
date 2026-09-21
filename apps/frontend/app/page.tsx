import type { Metadata } from "next";
import { AffiliateNote } from "@/features/home/affiliate-note";
import { Hero } from "@/features/home/hero";
import { HowItWorks } from "@/features/home/how-it-works";
import { MatchExample } from "@/features/home/match-example";
import { PublicHeader } from "@/components/public-header";
import { SiteFooter } from "@/components/site-footer";

const title = "Find a matching outfit for your partner";
const description =
  "Start with an outfit you love. CoupleOutfit helps you discover matching partner outfits from real products you can buy.";

export const metadata: Metadata = {
  title,
  description,
  alternates: { canonical: "/" },
  robots: { index: true, follow: true },
  openGraph: {
    type: "website",
    url: "/",
    siteName: "CoupleOutfit",
    title: `${title} | CoupleOutfit`,
    description,
  },
  twitter: {
    card: "summary",
    title: `${title} | CoupleOutfit`,
    description,
  },
};

export default function HomePage() {
  return (
    <div className="min-h-svh overflow-hidden">
      <PublicHeader />
      <main id="main-content" tabIndex={-1}>
        <Hero />
        <HowItWorks />
        <MatchExample />
        <AffiliateNote />
      </main>
      <SiteFooter />
    </div>
  );
}
