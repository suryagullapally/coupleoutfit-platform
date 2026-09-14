import { PageContainer } from "@/components/page-container";

export default function HomePage() {
  return (
    <div className="flex min-h-svh flex-col">
      <header className="py-7 sm:py-10">
        <PageContainer>
          <div className="flex items-center gap-3">
            <span aria-hidden="true" className="flex size-9 items-center justify-center rounded-full bg-ink text-sm font-bold text-canvas">co</span>
            <span className="text-xl font-semibold tracking-tight">CoupleOutfit</span>
          </div>
        </PageContainer>
      </header>
      <main id="main-content" tabIndex={-1} className="flex flex-1 items-center py-12 sm:py-20">
        <PageContainer>
          <div className="max-w-3xl">
            <p className="mb-8 inline-flex items-center gap-2 rounded-full border border-line px-4 py-2 text-xs font-semibold tracking-wide">
              <span aria-hidden="true" className="size-1.5 rounded-full bg-ink" />
              PHASE 0 · FRONTEND FOUNDATION
            </p>
            <h1 className="font-display text-5xl leading-[1.08] tracking-tight sm:text-7xl lg:text-8xl">
              A thoughtful start.<br />
              <span className="text-muted">Room to grow.</span>
            </h1>
            <p className="mt-8 max-w-md text-base leading-7 text-muted sm:text-lg sm:leading-8">
              The CoupleOutfit frontend foundation is in place. This is our starting point, with the experience still to come.
            </p>
          </div>
          <div aria-hidden="true" className="mt-14 h-px w-full bg-line sm:mt-20" />
          <p className="mt-5 text-sm leading-6 text-muted">Built with care. Taking shape, one step at a time.</p>
        </PageContainer>
      </main>
      <footer className="pb-7 text-xs text-muted sm:pb-10">
        <PageContainer>
          <div className="flex flex-wrap items-center justify-between gap-3">
            <span>CoupleOutfit</span>
            <span>Application foundation</span>
          </div>
        </PageContainer>
      </footer>
    </div>
  );
}
