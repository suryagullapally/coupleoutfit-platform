import { PageContainer } from "@/components/page-container";
import { homepageContent } from "@/features/home/content";
import { SourceEntry } from "@/features/home/source-entry";

export function Hero() {
  return (
    <section id="start" className="relative py-12 sm:py-18 lg:py-24" aria-labelledby="hero-title">
      <PageContainer>
        <div className="grid items-center gap-12 lg:grid-cols-[1.08fr_0.92fr] lg:gap-16">
          <div className="relative z-10">
            <p className="mb-5 inline-flex items-center gap-2 rounded-full border border-line bg-surface/75 px-3.5 py-2 text-[0.68rem] font-bold uppercase tracking-[0.17em] text-muted">
              <span aria-hidden="true" className="size-1.5 rounded-full bg-clay" />
              {homepageContent.eyebrow}
            </p>
            <h1 id="hero-title" className="max-w-3xl font-display text-[clamp(3.25rem,9vw,6.75rem)] leading-[0.91] tracking-[-0.055em] text-ink">
              {homepageContent.titleLead}<br /><span className="italic text-clay">{homepageContent.titleEmphasis}</span>
            </h1>
            <p className="mt-6 max-w-xl text-base leading-7 text-muted sm:text-lg sm:leading-8">{homepageContent.promise}</p>
            <div className="mt-8 max-w-2xl"><SourceEntry /></div>
          </div>
          <HeroEditorial />
        </div>
      </PageContainer>
    </section>
  );
}

function HeroEditorial() {
  return (
    <div className="relative mx-auto hidden aspect-[4/5] w-full max-w-[31rem] lg:block" aria-hidden="true">
      <div className="absolute inset-x-10 inset-y-0 rotate-2 overflow-hidden rounded-[3.5rem_3.5rem_2rem_2rem] bg-blush shadow-[0_30px_80px_rgba(23,54,45,0.16)]">
        <div className="absolute inset-0 bg-[linear-gradient(145deg,rgba(255,255,255,.32),transparent_45%)]" />
        <svg viewBox="0 0 420 520" className="absolute inset-0 size-full">
          <path d="M145 92c19-24 37-35 65-35s47 11 65 35l67 54-41 71-42-29 15 253H146l15-253-42 29-41-71 67-54Z" fill="#f9f5ed" />
          <path d="M172 71c5 26 17 42 38 42s33-16 38-42" fill="none" stroke="#17362d" strokeWidth="4" />
          <path d="M151 246h118M147 278h126M144 310h132" stroke="#a75a42" strokeWidth="18" opacity=".82" />
          <path d="m132 439 23-205M288 439l-23-205" stroke="#17362d" strokeWidth="3" opacity=".18" />
        </svg>
        <div className="absolute bottom-7 left-7 rounded-full bg-surface/90 px-4 py-2 text-xs font-bold tracking-wide text-ink">START WITH THEIR LOOK</div>
      </div>
      <div className="absolute -right-3 top-14 rounded-2xl bg-ink px-5 py-4 text-canvas shadow-xl">
        <span className="block text-[0.65rem] font-bold uppercase tracking-[0.18em] text-accent-deep">Style cues</span>
        <span className="mt-1 block font-display text-2xl italic">Warm · Tailored</span>
      </div>
      <div className="absolute -bottom-5 left-0 size-24 rounded-full bg-accent-deep/80" />
    </div>
  );
}
