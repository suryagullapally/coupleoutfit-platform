import Link from "next/link";
import { BrandMark } from "@/components/brand-mark";
import { PageContainer } from "@/components/page-container";
import { homepageContent } from "@/features/home/content";

export function SiteFooter() {
  return (
    <footer className="border-t border-line bg-surface py-10 sm:py-12">
      <PageContainer>
        <div className="flex flex-col gap-8 sm:flex-row sm:items-end sm:justify-between">
          <div>
            <Link href="/" className="inline-flex rounded-full" aria-label="CoupleOutfit home">
              <BrandMark />
            </Link>
            <p className="mt-3 max-w-sm text-sm leading-6 text-muted">Matching looks for two, starting with the outfit you already love.</p>
          </div>
          <nav aria-label="Legal navigation">
            <ul className="flex flex-wrap gap-x-6 gap-y-3 text-sm font-medium text-muted">
              {homepageContent.legalLinks.map((link) => (
                <li key={link.href}><Link className="rounded-sm underline-offset-4 hover:text-ink hover:underline" href={link.href}>{link.label}</Link></li>
              ))}
            </ul>
          </nav>
        </div>
        <p className="mt-10 border-t border-line pt-6 text-xs text-muted">© {new Date().getFullYear()} CoupleOutfit</p>
      </PageContainer>
    </footer>
  );
}
