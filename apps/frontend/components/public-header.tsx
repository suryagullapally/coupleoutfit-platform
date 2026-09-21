import Link from "next/link";
import { BrandMark } from "@/components/brand-mark";
import { PageContainer } from "@/components/page-container";

export function PublicHeader() {
  return (
    <header className="relative z-20 border-b border-line/70 bg-canvas/90 backdrop-blur-md">
      <PageContainer>
        <div className="flex min-h-18 items-center justify-between gap-5 py-3">
          <Link href="/" className="rounded-full" aria-label="CoupleOutfit home">
            <BrandMark />
          </Link>
          <nav aria-label="Primary navigation" className="flex items-center gap-1 sm:gap-3">
            <Link href="/#how-it-works" className="hidden min-h-11 items-center rounded-full px-4 text-sm font-semibold text-muted transition-colors hover:bg-surface hover:text-ink sm:inline-flex">
              How it works
            </Link>
            <Link href="/#start" className="inline-flex min-h-11 items-center rounded-full bg-ink px-4 text-sm font-semibold text-canvas transition-colors hover:bg-night sm:px-5">
              Find a match
            </Link>
          </nav>
        </div>
      </PageContainer>
    </header>
  );
}
