import type { Metadata } from "next";
import { LegalPage } from "@/components/legal-page";

export const metadata: Metadata = {
  title: "Terms",
  description: "Terms information for CoupleOutfit.",
  alternates: { canonical: "/terms" },
};

export default function TermsPage() {
  return (
    <LegalPage eyebrow="Public information" title="Terms">
      <p>CoupleOutfit is preparing its full terms for public launch. The final terms will describe use of the matching service, product information, outbound retailer links and service limitations.</p>
      <p>This homepage is currently an entry preview. It does not create matches, submit images or place orders.</p>
    </LegalPage>
  );
}
