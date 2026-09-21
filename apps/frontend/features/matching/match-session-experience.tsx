"use client";

import Link from "next/link";
import { useEffect, useRef, useState } from "react";
import { createMatchSessionApi } from "@/features/matching/api";
import { MatchResults } from "@/features/matching/match-results";
import type { MatchSessionResponse } from "@/features/matching/types";
import { isProcessing, matchErrorMessage, matchFailureMessage, waitForMatchSession } from "@/features/matching/workflow";

type View = "loading" | "processing" | "ready" | "failed";

export function MatchSessionExperience({ matchSessionId }: { matchSessionId: string | null }) {
  const [view, setView] = useState<View>(matchSessionId ? "loading" : "failed");
  const [message, setMessage] = useState(matchSessionId ? "" : "This match link is incomplete.");
  const [session, setSession] = useState<MatchSessionResponse | null>(null);
  const heading = useRef<HTMLHeadingElement>(null);

  useEffect(() => {
    if (!matchSessionId) return;
    const controller = new AbortController();
    async function load() {
      try {
        const api = createMatchSessionApi(window.location.origin);
        let current = await api.get(matchSessionId as string, controller.signal);
        setSession(current);
        if (isProcessing(current)) {
          setView("processing");
          current = await waitForMatchSession(api, matchSessionId as string, { signal: controller.signal });
          setSession(current);
        }
        if (current.status === "COMPLETED") setView("ready");
        else if (current.status === "FAILED") { setView("failed"); setMessage(matchFailureMessage(current.failureCode)); }
        requestAnimationFrame(() => heading.current?.focus());
      } catch (error) {
        if (controller.signal.aborted) return;
        setView("failed");
        setMessage(matchErrorMessage(error));
        requestAnimationFrame(() => heading.current?.focus());
      }
    }
    void load();
    return () => controller.abort();
  }, [matchSessionId]);

  if (view === "ready" && session) return <MatchResults session={session} />;

  return (
    <section className="mx-auto max-w-3xl rounded-panel border border-line bg-surface p-6 text-center shadow-[0_24px_70px_rgba(23,54,45,0.08)] sm:p-10" aria-labelledby="match-title">
      {(view === "loading" || view === "processing") && <>
        <span aria-hidden="true" className="mx-auto flex size-12 items-center justify-center rounded-full bg-accent"><span className="size-3 animate-pulse rounded-full bg-ink motion-reduce:animate-none" /></span>
        <h1 ref={heading} tabIndex={-1} id="match-title" className="mt-6 font-display text-4xl tracking-[-0.035em] sm:text-5xl">Finding compatible outfits…</h1>
        <p role="status" className="mx-auto mt-4 max-w-xl text-base leading-7 text-muted">Comparing styles and ranking the strongest matches for your preferences.</p>
      </>}
      {view === "failed" && <>
        <p className="text-xs font-bold uppercase tracking-[0.18em] text-clay">Match unavailable</p>
        <h1 ref={heading} tabIndex={-1} id="match-title" className="mt-3 font-display text-4xl tracking-[-0.035em] sm:text-5xl">We couldn&apos;t open this match.</h1>
        <p role="alert" className="mx-auto mt-4 max-w-xl text-base leading-7 text-muted">{message}</p>
        <div className="mt-7 flex flex-col justify-center gap-3 sm:flex-row">
          {session && isProcessing(session) && <button type="button" onClick={() => window.location.reload()} className="min-h-12 rounded-xl bg-ink px-6 text-sm font-semibold text-canvas">Check again</button>}
          {session ? <Link href={`/match?source=${encodeURIComponent(session.sourceItemId)}`} className="inline-flex min-h-12 items-center justify-center rounded-xl border border-ink/20 px-5 text-sm font-semibold text-ink hover:bg-canvas">Change match preferences</Link> : <Link href="/#start" className="inline-flex min-h-12 items-center justify-center rounded-xl border border-ink/20 px-5 text-sm font-semibold text-ink hover:bg-canvas">Choose another outfit</Link>}
        </div>
      </>}
    </section>
  );
}
