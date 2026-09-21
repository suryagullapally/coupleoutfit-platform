import Link from "next/link";

export function AffiliateDisclosureNote() {
  return (
    <aside className="flex flex-col gap-4 rounded-2xl border border-line bg-accent/55 p-5 sm:flex-row sm:items-center sm:justify-between" aria-labelledby="affiliate-note-title">
      <div className="flex max-w-3xl gap-4">
        <span aria-hidden="true" className="mt-1 flex size-9 shrink-0 items-center justify-center rounded-full bg-surface text-sm font-bold text-ink">i</span>
        <div>
          <h2 id="affiliate-note-title" className="text-sm font-semibold">A note about product links</h2>
          <p className="mt-1 text-sm leading-6 text-muted">CoupleOutfit may earn a commission when you buy through a product link, at no additional cost to you.</p>
        </div>
      </div>
      <Link href="/affiliate-disclosure" className="ml-13 inline-flex min-h-11 shrink-0 items-center text-sm font-semibold underline decoration-1 underline-offset-4 hover:text-clay sm:ml-0">Read our disclosure</Link>
    </aside>
  );
}
