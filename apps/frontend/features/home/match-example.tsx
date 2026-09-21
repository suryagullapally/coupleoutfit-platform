import { PageContainer } from "@/components/page-container";

export function MatchExample() {
  return (
    <section className="py-16 sm:py-24 lg:py-28" aria-labelledby="example-title">
      <PageContainer>
        <div className="mx-auto max-w-3xl text-center">
          <p className="text-xs font-bold uppercase tracking-[0.2em] text-clay">A shared visual language</p>
          <h2 id="example-title" className="mt-4 font-display text-4xl leading-tight tracking-[-0.035em] sm:text-6xl">Different outfits. Better together.</h2>
          <p className="mx-auto mt-5 max-w-2xl text-base leading-7 text-muted sm:text-lg">
            CoupleOutfit looks for the details that connect two looks — without making them identical.
          </p>
        </div>

        <figure className="relative mx-auto mt-10 grid max-w-5xl items-center gap-3 sm:mt-16 sm:grid-cols-[1fr_auto_1fr] sm:gap-8">
          <OutfitCard kind="source" label="Source outfit" caption="Soft tailoring · warm neutrals" />
          <div className="relative z-10 mx-auto flex size-12 rotate-90 items-center justify-center rounded-full border border-line bg-surface text-ink shadow-lg sm:size-14 sm:rotate-0" aria-hidden="true">
            <svg viewBox="0 0 24 24" className="size-6 fill-none stroke-current" strokeWidth="1.6"><path d="M5 12h14m-5-5 5 5-5 5" strokeLinecap="round" strokeLinejoin="round" /></svg>
          </div>
          <OutfitCard kind="match" label="Partner match" caption="Relaxed layers · balanced colour" />
          <figcaption className="sm:col-span-3 mt-2 text-center text-xs leading-5 text-muted">
            Illustrative style example — not live matching output or retailer inventory.
          </figcaption>
        </figure>
        <div className="mt-10 text-center sm:mt-14">
          <a href="#start" className="inline-flex min-h-12 items-center rounded-full bg-ink px-6 text-sm font-semibold text-canvas transition-colors hover:bg-night">
            Find your partner&apos;s match
          </a>
        </div>
      </PageContainer>
    </section>
  );
}

function OutfitCard({ kind, label, caption }: { kind: "source" | "match"; label: string; caption: string }) {
  const source = kind === "source";
  return (
    <div className="overflow-hidden rounded-panel border border-line bg-surface shadow-[0_22px_60px_rgba(23,54,45,0.08)]">
      <div className={`relative aspect-[5/4] overflow-hidden sm:aspect-[4/5] ${source ? "bg-blush" : "bg-accent"}`}>
        <div className="absolute left-5 top-5 z-10 rounded-full bg-surface/90 px-3 py-2 text-[0.65rem] font-bold uppercase tracking-[0.16em] text-ink">{label}</div>
        {source ? <SourceLook /> : <PartnerLook />}
      </div>
      <div className="flex items-center justify-between gap-4 p-4 sm:p-5">
        <p className="text-sm font-semibold text-ink">{caption}</p>
        <span className={`size-3 shrink-0 rounded-full ${source ? "bg-clay" : "bg-ink"}`} aria-hidden="true" />
      </div>
    </div>
  );
}

function SourceLook() {
  return (
    <svg role="img" aria-label="Illustration of a cream striped top with rust trousers" viewBox="0 0 420 520" className="absolute inset-0 size-full">
      <title>Source outfit: cream striped top with rust trousers</title>
      <ellipse cx="210" cy="490" rx="125" ry="18" fill="#17362d" opacity=".08" />
      <path d="M125 86c23-24 49-34 85-34s62 10 85 34l63 61-42 62-48-35 12 166H140l12-166-48 35-42-62 63-61Z" fill="#fffaf1" />
      <path d="M168 67c6 24 19 38 42 38s36-14 42-38" fill="none" stroke="#17362d" strokeWidth="4" />
      <path d="M143 223h134M140 257h140M138 291h144" stroke="#a75a42" strokeWidth="15" opacity=".82" />
      <path d="M146 340h128l30 144h-77l-17-99-17 99h-77l30-144Z" fill="#a75a42" />
    </svg>
  );
}

function PartnerLook() {
  return (
    <svg role="img" aria-label="Illustration of a deep green jacket with a sand shirt and trousers" viewBox="0 0 420 520" className="absolute inset-0 size-full">
      <title>Partner match: deep green jacket with a sand shirt and trousers</title>
      <ellipse cx="210" cy="490" rx="125" ry="18" fill="#17362d" opacity=".08" />
      <path d="M130 92c20-27 48-40 80-40s60 13 80 40l55 67-48 50-38-38 13 170H148l13-170-38 38-48-50 55-67Z" fill="#17362d" />
      <path d="M177 69c4 24 15 38 33 38s29-14 33-38l24 27-32 178h-50L153 96l24-27Z" fill="#f2e7d2" />
      <path d="m148 341 124 0 27 143h-70l-19-102-19 102h-70l27-143Z" fill="#786d58" />
      <circle cx="210" cy="153" r="4" fill="#a75a42" /><circle cx="210" cy="187" r="4" fill="#a75a42" />
    </svg>
  );
}
