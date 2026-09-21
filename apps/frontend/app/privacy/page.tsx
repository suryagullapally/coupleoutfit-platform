import type { Metadata } from "next";
import { LegalPage } from "@/components/legal-page";

export const metadata: Metadata = {
  title: "Privacy",
  description: "Privacy information for CoupleOutfit.",
  alternates: { canonical: "/privacy" },
};

export default function PrivacyPage() {
  return (
    <LegalPage eyebrow="Public information" title="Privacy">
      <p>CoupleOutfit is preparing its full privacy notice for public launch. It will explain what information the service uses, why it is needed, how long it is kept and the choices available to visitors.</p>
      <p>The homepage preview does not submit product links or upload images. Do not provide personal or sensitive information through the preview controls.</p>
    </LegalPage>
  );
}
