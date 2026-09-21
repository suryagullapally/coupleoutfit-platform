import type { ReactNode } from "react";
import { PageContainer } from "@/components/page-container";
import { PublicHeader } from "@/components/public-header";
import { SiteFooter } from "@/components/site-footer";

export function LegalPage({ eyebrow, title, children }: { eyebrow: string; title: string; children: ReactNode }) {
  return (
    <div className="min-h-svh">
      <PublicHeader />
      <main id="main-content" tabIndex={-1} className="py-16 sm:py-24">
        <PageContainer>
          <article className="mx-auto max-w-3xl rounded-panel border border-line bg-surface p-7 shadow-[0_24px_70px_rgba(23,54,45,0.08)] sm:p-12">
            <p className="text-xs font-bold uppercase tracking-[0.2em] text-clay">{eyebrow}</p>
            <h1 className="mt-4 font-display text-5xl leading-tight tracking-[-0.04em] sm:text-6xl">{title}</h1>
            <div className="mt-8 space-y-5 text-base leading-7 text-muted">{children}</div>
          </article>
        </PageContainer>
      </main>
      <SiteFooter />
    </div>
  );
}
