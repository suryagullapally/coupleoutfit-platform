"use client";

/* Product image hosts come from approved backend feed data and are not enumerable in frontend configuration. */
/* eslint-disable @next/next/no-img-element */

import Link from "next/link";
import { useEffect, useRef, useState, type RefObject } from "react";
import { AffiliateDisclosureNote } from "@/components/affiliate-disclosure-note";
import type { MatchResult, MatchSessionResponse } from "@/features/matching/types";
import { availabilityLabel, matchModeLabel, offerPrice, scoreBandLabel, targetLabel } from "@/features/matching/presentation";

export function MatchResults({ session }: { session: MatchSessionResponse }) {
  const heading = useRef<HTMLHeadingElement>(null);
  useEffect(() => { heading.current?.focus(); }, []);
  if (session.results.length === 0) return <EmptyResults session={session} heading={heading} />;
  return (
    <div className="mx-auto max-w-6xl">
      <header className="max-w-3xl">
        <p className="text-xs font-bold uppercase tracking-[0.18em] text-clay">Your partner matches</p>
        <h1 ref={heading} tabIndex={-1} id="match-title" className="mt-3 font-display text-4xl leading-tight tracking-[-0.035em] sm:text-6xl">Looks designed to work together.</h1>
        <p className="mt-4 text-base leading-7 text-muted">{session.results.length} ranked {targetLabel(session.configuration.partnerTarget)} {session.results.length === 1 ? "match" : "matches"}, using your {matchModeLabel(session.configuration.matchMode).toLowerCase()} preference.</p>
        {session.configuration.sameRetailerOnly && <p className="mt-3 inline-flex rounded-full bg-accent/65 px-3 py-1.5 text-sm font-semibold text-ink">Matched within the same retailer</p>}
      </header>

      <ol className="mt-8 grid gap-6 md:grid-cols-2">
        {session.results.map((result, index) => (
          <li key={result.resultId} className={index === 0 ? "md:col-span-2" : ""}>
            <MatchResultCard result={result} localeCode={session.market.localeCode} primary={index === 0} />
          </li>
        ))}
      </ol>
      <div className="mt-8"><AffiliateDisclosureNote /></div>
    </div>
  );
}

function MatchResultCard({ result, localeCode, primary }: { result: MatchResult; localeCode: string; primary: boolean }) {
  const price = offerPrice(result.offer.amountMinor, result.offer.currencyCode, localeCode);
  const band = scoreBandLabel(result.scoreBand);
  return (
    <article className={`overflow-hidden rounded-panel border border-line bg-surface shadow-[0_18px_60px_rgba(23,54,45,0.07)] ${primary ? "lg:grid lg:grid-cols-[0.9fr_1.1fr]" : ""}`} aria-labelledby={`product-${result.resultId}`}>
      <ProductImage result={result} priority={primary} />
      <div className="flex flex-col p-5 sm:p-7">
        <div className="flex items-start justify-between gap-4">
          <div>
            <p className="text-xs font-bold uppercase tracking-[0.16em] text-clay">{primary ? "Best current match" : `Match ${result.rank}`}</p>
            <h2 id={`product-${result.resultId}`} className="mt-2 font-display text-3xl leading-tight text-ink">{result.product.name}</h2>
            <p className="mt-2 text-sm font-semibold text-muted">{result.product.brand ? `${result.product.brand} · ` : ""}{result.retailer.name}</p>
          </div>
          <div className="shrink-0 rounded-2xl bg-ink px-4 py-3 text-center text-canvas" aria-label={`${result.coupleMatchScore} out of 100, ${band}`}>
            <span className="block font-display text-3xl leading-none">{result.coupleMatchScore}</span>
            <span className="mt-1 block text-xs font-bold uppercase tracking-[0.1em]">{band}</span>
          </div>
        </div>

        <div className="mt-6 border-t border-line pt-5">
          <h3 className="text-sm font-bold text-ink">Why it works</h3>
          {result.explanation.reasons.length ? (
            <ul className="mt-3 space-y-2">
              {result.explanation.reasons.map((reason) => <li key={reason} className="flex gap-2 text-sm leading-6 text-muted"><span aria-hidden="true" className="text-clay">✓</span><span>{reason}</span></li>)}
            </ul>
          ) : <p className="mt-2 text-sm leading-6 text-muted">No additional match notes were provided.</p>}
        </div>

        <div className="mt-6 flex flex-wrap items-end justify-between gap-4 border-t border-line pt-5">
          <div>
            <p className="text-sm font-semibold text-ink">{result.retailer.name}</p>
            <p className="mt-1 text-2xl font-bold text-ink">{price ?? "Price unavailable"}</p>
            <p className="mt-1 text-sm text-muted">{availabilityLabel(result.offer.availability)}</p>
          </div>
          {result.purchase.available && result.purchase.url ? (
            <a href={result.purchase.url} className="inline-flex min-h-12 items-center justify-center rounded-xl bg-clay px-6 text-sm font-semibold text-white hover:bg-clay/90" aria-label={`View ${result.product.name} at ${result.retailer.name}`}>View at retailer</a>
          ) : <span className="inline-flex min-h-12 items-center rounded-xl border border-line px-5 text-sm font-semibold text-muted">Shopping link unavailable</span>}
        </div>
      </div>
    </article>
  );
}

function ProductImage({ result, priority }: { result: MatchResult; priority: boolean }) {
  const [failed, setFailed] = useState(false);
  const alt = result.product.brand ? `${result.product.name} by ${result.product.brand}` : result.product.name;
  return (
    <div className={`relative flex aspect-[4/3] items-center justify-center overflow-hidden bg-accent/45 ${priority ? "lg:aspect-[4/5]" : ""}`}>
      {result.offer.imageUrl && !failed ? (
        <img src={result.offer.imageUrl} alt={alt} onError={() => setFailed(true)} loading={priority ? "eager" : "lazy"} fetchPriority={priority ? "high" : "auto"} decoding="async" referrerPolicy="no-referrer" className="size-full object-cover" />
      ) : (
        <div className="grid place-items-center px-5 text-center text-muted" role="img" aria-label={`Image unavailable for ${result.product.name}`}>
          <svg aria-hidden="true" viewBox="0 0 64 64" className="size-16 fill-none stroke-ink/45" strokeWidth="1.6"><path d="M22 15c3-4 6-6 10-6s7 2 10 6l12 10-7 12-7-5 3 23H21l3-23-7 5-7-12 12-10Z" strokeLinejoin="round" /><path d="M26 12c1 5 3 8 6 8s5-3 6-8" /></svg>
          <span className="mt-3 text-sm font-medium">Product image unavailable</span>
        </div>
      )}
    </div>
  );
}

function EmptyResults({ session, heading }: { session: MatchSessionResponse; heading: RefObject<HTMLHeadingElement | null> }) {
  return (
    <section className="mx-auto max-w-3xl rounded-panel border border-line bg-surface p-6 text-center shadow-[0_24px_70px_rgba(23,54,45,0.08)] sm:p-10" aria-labelledby="match-title">
      <p className="text-xs font-bold uppercase tracking-[0.18em] text-clay">No strong matches yet</p>
      <h1 ref={heading} tabIndex={-1} id="match-title" className="mt-3 font-display text-4xl tracking-[-0.035em] sm:text-5xl">This outfit deserves another look.</h1>
      <p role="status" className="mx-auto mt-4 max-w-xl text-base leading-7 text-muted">We couldn&apos;t find a sufficiently strong purchasable partner match for these preferences.</p>
      {session.configuration.sameRetailerOnly && <p className="mx-auto mt-3 max-w-xl text-sm leading-6 text-muted">Your search stayed within the same retailer. You can return and choose a wider search.</p>}
      <div className="mt-7 flex flex-col justify-center gap-3 sm:flex-row">
        <Link href={`/match?source=${encodeURIComponent(session.sourceItemId)}`} className="inline-flex min-h-12 items-center justify-center rounded-xl bg-ink px-6 text-sm font-semibold text-canvas">Change match preferences</Link>
        <Link href="/#start" className="inline-flex min-h-12 items-center justify-center rounded-xl border border-ink/20 px-5 text-sm font-semibold text-ink hover:bg-canvas">Try another outfit</Link>
      </div>
    </section>
  );
}
