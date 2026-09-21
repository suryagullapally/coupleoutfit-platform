import { PageContainer } from "@/components/page-container";
import { AffiliateDisclosureNote } from "@/components/affiliate-disclosure-note";

export function AffiliateNote() {
  return (
    <section className="pb-16 sm:pb-24 lg:pb-28" aria-label="Affiliate disclosure">
      <PageContainer>
        <AffiliateDisclosureNote />
      </PageContainer>
    </section>
  );
}
