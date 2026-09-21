import { PageContainer } from "@/components/page-container";
import { homepageContent } from "@/features/home/content";

export function HowItWorks() {
  return (
    <section id="how-it-works" className="bg-ink py-16 text-canvas sm:py-24 lg:py-28" aria-labelledby="how-title">
      <PageContainer>
        <div className="grid gap-8 sm:gap-10 lg:grid-cols-[0.7fr_1.3fr] lg:gap-16">
          <div>
            <p className="text-xs font-bold uppercase tracking-[0.2em] text-accent-deep">How it works</p>
            <h2 id="how-title" className="mt-4 max-w-md font-display text-4xl leading-tight tracking-[-0.035em] sm:text-5xl">From one good look to two.</h2>
          </div>
          <ol className="grid gap-4 sm:grid-cols-3">
            {homepageContent.steps.map((step) => (
              <li key={step.number} className="rounded-panel border border-canvas/15 bg-canvas/[0.055] p-5 sm:min-h-64 sm:p-6">
                <span className="font-display text-2xl italic text-accent-deep" aria-hidden="true">{step.number}</span>
                <h3 className="mt-7 text-lg font-semibold tracking-tight sm:mt-10">{step.title}</h3>
                <p className="mt-3 text-sm leading-6 text-canvas/70">{step.description}</p>
              </li>
            ))}
          </ol>
        </div>
      </PageContainer>
    </section>
  );
}
