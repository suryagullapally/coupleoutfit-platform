"use client";

import Link from "next/link";
import { useEffect, useRef, useState } from "react";
import { ApiError } from "@/lib/api/errors";
import { createSourceItemApi } from "@/features/source/api";
import type { SourceItemResponse } from "@/features/source/types";
import { SubmissionGate } from "@/features/source/workflow";
import { createClothingAnalysisApi } from "@/features/analysis/api";
import type { ClothingAnalysisResponse, ClothingAttributes } from "@/features/analysis/types";
import { analysisErrorMessage, saveCorrection, waitForAnalysis } from "@/features/analysis/workflow";
import { AttributeEditor, AttributeSummary } from "@/features/analysis/attribute-review";
import { MatchPreferences } from "@/features/matching/match-preferences";
import { eligibleAnalysisRevision } from "@/features/matching/workflow";

type ViewState = "loading" | "ready" | "processing" | "review" | "failed";

export function AnalysisExperience({ sourceItemId }: { sourceItemId: string | null }) {
  const [view, setView] = useState<ViewState>(sourceItemId ? "loading" : "failed");
  const [source, setSource] = useState<SourceItemResponse | null>(null);
  const [analysis, setAnalysis] = useState<ClothingAnalysisResponse | null>(null);
  const [message, setMessage] = useState<string | null>(sourceItemId ? null : "This outfit link is incomplete. Start again from the homepage.");
  const [editing, setEditing] = useState(false);
  const [saving, setSaving] = useState(false);
  const [saveError, setSaveError] = useState<string | null>(null);
  const [confirmed, setConfirmed] = useState(false);
  const startGate = useRef(new SubmissionGate());
  const correctionGate = useRef(new SubmissionGate());
  const activeCheck = useRef<AbortController | null>(null);
  const reviewHeading = useRef<HTMLHeadingElement>(null);
  const matchRevision = eligibleAnalysisRevision(analysis);

  function apis() {
    const origin = window.location.origin;
    return { sources: createSourceItemApi(origin), analyses: createClothingAnalysisApi(origin) };
  }

  function showAnalysis(result: ClothingAnalysisResponse) {
    setAnalysis(result);
    setEditing(false);
    setConfirmed(false);
    if (result.status === "COMPLETED" && result.currentRevision) {
      setView("review");
      requestAnimationFrame(() => reviewHeading.current?.focus());
    } else if (result.status === "FAILED") {
      setView("failed");
      setMessage(failureMessage(result.failureCode));
    } else {
      setView("processing");
    }
  }

  useEffect(() => {
    if (!sourceItemId) return;
    const controller = new AbortController();
    async function load() {
      try {
        const clients = apis();
        const ownedSource = await clients.sources.get(sourceItemId as string, controller.signal);
        setSource(ownedSource);
        if (ownedSource.status !== "READY_FOR_ANALYSIS") {
          setView("failed");
          setMessage("This outfit is not ready for analysis. Return to the source step and try again.");
          return;
        }
        try {
          const current = await clients.analyses.get(sourceItemId as string, controller.signal);
          if (current.status === "PENDING" || current.status === "IN_PROGRESS") {
            setAnalysis(current);
            setView("processing");
            showAnalysis(await waitForAnalysis(clients.analyses, sourceItemId as string, { signal: controller.signal }));
          } else showAnalysis(current);
        } catch (error) {
          if (error instanceof ApiError && error.code === "ANALYSIS_NOT_FOUND") {
            setView("ready");
            setMessage(null);
          } else throw error;
        }
      } catch (error) {
        if (controller.signal.aborted) return;
        setView("failed");
        setMessage(analysisErrorMessage(error));
      }
    }
    void load();
    return () => controller.abort();
  }, [sourceItemId]);

  useEffect(() => () => activeCheck.current?.abort(), []);

  async function startAnalysis() {
    if (!sourceItemId || !startGate.current.tryEnter()) return;
    const controller = new AbortController();
    activeCheck.current = controller;
    setView("processing");
    setMessage(null);
    try {
      const client = apis().analyses;
      const result = await client.start(sourceItemId, controller.signal);
      setAnalysis(result);
      showAnalysis(result.status === "PENDING" || result.status === "IN_PROGRESS" ? await waitForAnalysis(client, sourceItemId, { signal: controller.signal }) : result);
    } catch (error) {
      if (controller.signal.aborted) return;
      try {
        const current = await apis().analyses.get(sourceItemId);
        if (current.status === "FAILED") { showAnalysis(current); return; }
      } catch { /* The original safe error remains the useful customer message. */ }
      setView("failed");
      setMessage(analysisErrorMessage(error));
    } finally {
      if (activeCheck.current === controller) activeCheck.current = null;
      startGate.current.leave();
    }
  }

  async function checkStatus() {
    if (!sourceItemId || !startGate.current.tryEnter()) return;
    const controller = new AbortController();
    activeCheck.current = controller;
    setView("processing");
    setMessage(null);
    try {
      const client = apis().analyses;
      const current = await client.get(sourceItemId, controller.signal);
      setAnalysis(current);
      showAnalysis(current.status === "PENDING" || current.status === "IN_PROGRESS" ? await waitForAnalysis(client, sourceItemId, { signal: controller.signal }) : current);
    } catch (error) {
      if (controller.signal.aborted) return;
      setView("failed");
      setMessage(analysisErrorMessage(error));
    } finally {
      if (activeCheck.current === controller) activeCheck.current = null;
      startGate.current.leave();
    }
  }

  async function correct(attributes: ClothingAttributes) {
    const revision = analysis?.currentRevision;
    if (!sourceItemId || !revision || !correctionGate.current.tryEnter()) return;
    setSaving(true);
    setSaveError(null);
    try {
      const result = await saveCorrection(apis().analyses, sourceItemId, revision.id, attributes);
      showAnalysis(result.analysis);
      setMessage(result.kind === "conflict" ? "A newer review was saved. The latest outfit details are shown below." : "Your corrections have been saved.");
    } catch (error) {
      setSaveError(analysisErrorMessage(error));
    } finally {
      setSaving(false);
      correctionGate.current.leave();
    }
  }

  return (
    <div className="grid gap-6 lg:grid-cols-[0.72fr_1.28fr] lg:items-start lg:gap-8">
      <SourceContext source={source} />
      <section className="rounded-panel border border-line bg-surface p-5 shadow-[0_24px_70px_rgba(23,54,45,0.08)] sm:p-8" aria-labelledby="analysis-title">
        {view === "loading" && <Status title="Finding your outfit…" detail="Checking the source saved in this browser session." />}
        {view === "ready" && (
          <div>
            <p className="text-xs font-bold uppercase tracking-[0.18em] text-clay">Ready to analyse</p>
            <h1 id="analysis-title" className="mt-3 font-display text-4xl leading-tight tracking-[-0.035em] sm:text-5xl">Let&apos;s read the look.</h1>
            <p className="mt-4 max-w-xl text-base leading-7 text-muted">CoupleOutfit will identify the garment, colours, pattern and style details for you to review.</p>
            <button type="button" onClick={startAnalysis} className="mt-7 min-h-12 rounded-xl bg-ink px-6 text-sm font-semibold text-canvas hover:bg-night">Analyse this outfit</button>
          </div>
        )}
        {view === "processing" && <Status title="Analysing your outfit…" detail="Looking at the colours, shape and style details. This can take a moment." />}
        {view === "failed" && (
          <div>
            <p className="text-xs font-bold uppercase tracking-[0.18em] text-clay">Analysis unavailable</p>
            <h1 id="analysis-title" className="mt-3 font-display text-4xl leading-tight tracking-[-0.035em] sm:text-5xl">We couldn&apos;t finish this look.</h1>
            <p role="alert" className="mt-4 max-w-xl text-base leading-7 text-muted">{message}</p>
            <div className="mt-7 flex flex-wrap gap-3">
              {(analysis?.status === "PENDING" || analysis?.status === "IN_PROGRESS") && <button type="button" onClick={checkStatus} className="min-h-12 rounded-xl bg-ink px-6 text-sm font-semibold text-canvas">Check again</button>}
              <Link href="/#start" className="inline-flex min-h-12 items-center rounded-xl border border-ink/20 px-5 text-sm font-semibold text-ink hover:bg-canvas">Choose another outfit</Link>
            </div>
          </div>
        )}
        {view === "review" && matchRevision && (
          confirmed ? (
            <MatchPreferences key={matchRevision.id} sourceItemId={sourceItemId as string} revision={matchRevision} onBack={() => setConfirmed(false)} />
          ) : (
            <div>
              <p className="text-xs font-bold uppercase tracking-[0.18em] text-clay">Outfit review</p>
              <h1 ref={reviewHeading} tabIndex={-1} id="analysis-title" className="mt-3 font-display text-4xl leading-tight tracking-[-0.035em] sm:text-5xl">Here&apos;s what we understood.</h1>
              <p className="mt-4 max-w-2xl text-base leading-7 text-muted">Check these details before continuing. If anything looks off, make a quick correction.</p>
              {message && <p role="status" className="mt-5 rounded-xl bg-accent/55 px-4 py-3 text-sm leading-6 text-ink">{message}</p>}
              <div className="mt-8">
                {editing ? (
                  <AttributeEditor key={matchRevision.id} revision={matchRevision} saving={saving} submitError={saveError} onCancel={() => { setEditing(false); setSaveError(null); }} onSave={correct} />
                ) : (
                  <>
                    <AttributeSummary revision={matchRevision} />
                    <div className="mt-8 flex flex-col-reverse gap-3 border-t border-line pt-6 sm:flex-row sm:justify-end">
                      <button type="button" onClick={() => { setEditing(true); setMessage(null); }} className="min-h-12 rounded-xl border border-ink/20 px-5 text-sm font-semibold text-ink hover:bg-canvas">Correct details</button>
                      <button type="button" onClick={() => { setConfirmed(true); setMessage(null); }} className="min-h-12 rounded-xl bg-ink px-6 text-sm font-semibold text-canvas hover:bg-night">These details look right</button>
                    </div>
                  </>
                )}
              </div>
            </div>
          )
        )}
      </section>
    </div>
  );
}

function SourceContext({ source }: { source: SourceItemResponse | null }) {
  return (
    <aside className="rounded-panel bg-ink p-5 text-canvas sm:p-7 lg:sticky lg:top-6" aria-label="Source outfit">
      <div aria-hidden="true" className="flex aspect-[16/10] items-center justify-center rounded-2xl bg-[linear-gradient(145deg,rgba(220,233,200,.28),rgba(234,216,207,.18))]">
        <svg viewBox="0 0 64 64" className="size-20 fill-none stroke-accent-deep" strokeWidth="1.7"><path d="M22 15c3-4 6-6 10-6s7 2 10 6l12 10-7 12-7-5 3 23H21l3-23-7 5-7-12 12-10Z" strokeLinejoin="round" /><path d="M26 12c1 5 3 8 6 8s5-3 6-8" /></svg>
      </div>
      <p className="mt-5 text-xs font-bold uppercase tracking-[0.18em] text-accent-deep">Source outfit</p>
      <p className="mt-2 font-display text-3xl leading-tight">{source?.sourceType === "PRODUCT_URL" ? "Product page outfit" : source?.sourceType === "IMAGE_UPLOAD" ? "Uploaded outfit image" : "Your saved outfit"}</p>
      <p className="mt-3 text-sm leading-6 text-canvas/70">{source?.asset?.status === "VERIFIED" ? `${source.asset.mediaType?.replace("image/", "").toUpperCase() ?? "Image"}${source.asset.width && source.asset.height ? ` · ${source.asset.width} × ${source.asset.height}` : ""}` : "Private source details remain protected."}</p>
    </aside>
  );
}

function Status({ title, detail }: { title: string; detail: string }) {
  return (
    <div role="status" aria-live="polite" className="py-8 sm:py-12">
      <span aria-hidden="true" className="flex size-12 items-center justify-center rounded-full bg-accent"><span className="size-3 animate-pulse rounded-full bg-ink motion-reduce:animate-none" /></span>
      <h1 id="analysis-title" className="mt-6 font-display text-4xl leading-tight tracking-[-0.035em] sm:text-5xl">{title}</h1>
      <p className="mt-4 max-w-lg text-base leading-7 text-muted">{detail}</p>
    </div>
  );
}

function failureMessage(code: string | null): string {
  if (code === "ANALYSIS_IMAGE_UNSUPPORTED") return "We couldn't read enough from this outfit. Choose another image or product page.";
  if (code === "ANALYSIS_TIMEOUT" || code === "ANALYSIS_PROVIDER_RATE_LIMITED" || code === "ANALYSIS_PROVIDER_UNAVAILABLE") return "Outfit analysis is temporarily unavailable. Choose another outfit or return later.";
  return "This outfit could not be analysed. Choose another source to continue.";
}
