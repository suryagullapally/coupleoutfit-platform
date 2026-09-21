import type { Metadata } from "next";
import { LegalPage } from "@/components/legal-page";

export const metadata: Metadata = {
  title: "Affiliate disclosure",
  description: "How affiliate product links support CoupleOutfit.",
  alternates: { canonical: "/affiliate-disclosure" },
};

export default function AffiliateDisclosurePage() {
  return (
    <LegalPage eyebrow="Transparency" title="Affiliate disclosure">
      <p>CoupleOutfit may earn a commission when you follow a product link and make a purchase. This does not add a separate charge to your purchase.</p>
      <p>Product availability, prices and retailer terms are controlled by the retailer and may change. Affiliate relationships do not determine the Couple Match Score.</p>
    </LegalPage>
  );
}
