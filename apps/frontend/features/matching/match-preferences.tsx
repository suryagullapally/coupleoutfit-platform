"use client";

import { useEffect, useRef, useState, type FormEvent } from "react";
import { useRouter } from "next/navigation";
import type { AnalysisRevision } from "@/features/analysis/types";
import { tokenLabel } from "@/features/analysis/vocabulary";
import { SubmissionGate } from "@/features/source/workflow";
import { createMatchSessionApi } from "@/features/matching/api";
import { DEFAULT_MATCH_MODE, type CreateMatchSessionRequest, type MatchMode, type ProductTarget } from "@/features/matching/types";
import { isProcessing, MatchAttemptKeys, matchErrorMessage, matchFailureMessage, matchSessionPath, waitForMatchSession } from "@/features/matching/workflow";

const partnerOptions: ReadonlyArray<{ value: ProductTarget; label: string }> = [
  { value: "MENSWEAR", label: "Man" },
  { value: "WOMENSWEAR", label: "Woman" },
];

const modeOptions: ReadonlyArray<{ value: MatchMode; label: string; description: string }> = [
  { value: "EXACT", label: "Exact", description: "A closely matching look." },
  { value: "COORDINATED", label: "Coordinated", description: "Designed to look great together without being identical." },
  { value: "COMPLEMENTARY", label: "Complementary", description: "A contrasting look that still works with your outfit." },
];

export function MatchPreferences({ sourceItemId, revision, onBack }: {
  sourceItemId: string;
  revision: AnalysisRevision;
  onBack(): void;
}) {
  const router = useRouter();
  const [partnerTarget, setPartnerTarget] = useState<ProductTarget | null>(null);
  const [matchMode, setMatchMode] = useState<MatchMode>(DEFAULT_MATCH_MODE);
  const [sameRetailerOnly, setSameRetailerOnly] = useState(false);
  const [partnerError, setPartnerError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const [processing, setProcessing] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const gate = useRef(new SubmissionGate());
  const activeRequest = useRef<AbortController | null>(null);
  const idempotency = useRef(new MatchAttemptKeys());
  const heading = useRef<HTMLHeadingElement>(null);
  const partnerGroup = useRef<HTMLFieldSetElement>(null);

  useEffect(() => {
    heading.current?.focus();
    return () => activeRequest.current?.abort();
  }, []);

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!partnerTarget) {
      setPartnerError("Choose who you are finding an outfit for.");
      partnerGroup.current?.focus();
      return;
    }
    if (!gate.current.tryEnter()) return;
    const request: CreateMatchSessionRequest = { sourceItemId, analysisRevisionId: revision.id, partnerTarget, matchMode, sameRetailerOnly };
    const idempotencyKey = idempotency.current.keyFor(request);
    const controller = new AbortController();
    activeRequest.current = controller;
    setSubmitting(true);
    setProcessing(false);
    setError(null);
    try {
      const api = createMatchSessionApi(window.location.origin);
      let session = await api.create(request, idempotencyKey, controller.signal);
      if (isProcessing(session)) {
        setProcessing(true);
        session = await waitForMatchSession(api, session.id, { signal: controller.signal });
      }
      if (session.status === "FAILED") {
        setError(matchFailureMessage(session.failureCode));
        return;
      }
      if (session.status === "COMPLETED") router.push(matchSessionPath(session.id));
    } catch (cause) {
      if (!controller.signal.aborted) setError(matchErrorMessage(cause));
    } finally {
      if (activeRequest.current === controller) activeRequest.current = null;
      setSubmitting(false);
      setProcessing(false);
      gate.current.leave();
    }
  }

  return (
    <div>
      <p className="text-xs font-bold uppercase tracking-[0.18em] text-clay">Match preferences</p>
      <h1 ref={heading} tabIndex={-1} id="analysis-title" className="mt-3 font-display text-4xl leading-tight tracking-[-0.035em] sm:text-5xl">Who are we dressing?</h1>
      <p className="mt-4 max-w-2xl text-base leading-7 text-muted">We understand the outfit. Now choose the kind of partner match you want.</p>

      <div className="mt-6 flex flex-wrap gap-2" aria-label="Reviewed outfit summary">
        <span className="rounded-full bg-accent/60 px-3 py-1.5 text-sm font-medium text-ink">{tokenLabel(revision.attributes.category)}</span>
        {revision.attributes.primaryColors.map((color) => <span key={color} className="rounded-full bg-accent/60 px-3 py-1.5 text-sm font-medium text-ink">{tokenLabel(color)}</span>)}
        <span className="rounded-full bg-accent/60 px-3 py-1.5 text-sm font-medium text-ink">{tokenLabel(revision.attributes.formality)}</span>
      </div>

      <form onSubmit={submit} className="mt-8 space-y-8" noValidate>
        <fieldset ref={partnerGroup} tabIndex={-1} aria-describedby={partnerError ? "partner-help partner-error" : "partner-help"} aria-invalid={partnerError ? true : undefined}>
          <legend className="text-base font-bold text-ink">Finding an outfit for</legend>
          <p id="partner-help" className="mt-1 text-sm leading-6 text-muted">Choose your partner&apos;s clothing range.</p>
          <div className="mt-3 grid grid-cols-2 gap-3">
            {partnerOptions.map((option) => (
              <label key={option.value} className={`relative flex min-h-14 cursor-pointer items-center justify-between rounded-xl border px-4 py-3 text-base font-semibold transition-colors has-focus-visible:outline-3 has-focus-visible:outline-offset-3 has-focus-visible:outline-focus ${partnerTarget === option.value ? "border-ink bg-ink text-canvas" : "border-line bg-canvas text-ink hover:border-muted"}`}>
                <input type="radio" name="partnerTarget" value={option.value} checked={partnerTarget === option.value} onChange={() => { setPartnerTarget(option.value); setPartnerError(null); setError(null); }} className="sr-only" />
                <span>{option.label}</span>
                <span className="text-sm" aria-hidden="true">{partnerTarget === option.value ? "✓ Selected" : "Choose"}</span>
              </label>
            ))}
          </div>
          {partnerError && <p id="partner-error" role="alert" className="mt-2 text-sm font-medium text-clay">{partnerError}</p>}
        </fieldset>

        <fieldset>
          <legend className="text-base font-bold text-ink">How should the looks relate?</legend>
          <div className="mt-3 grid gap-3 sm:grid-cols-3">
            {modeOptions.map((option) => (
              <label key={option.value} className={`relative cursor-pointer rounded-2xl border p-4 transition-colors has-focus-visible:outline-3 has-focus-visible:outline-offset-3 has-focus-visible:outline-focus ${matchMode === option.value ? "border-ink bg-ink text-canvas" : "border-line bg-canvas text-ink hover:border-muted"}`}>
                <input type="radio" name="matchMode" value={option.value} checked={matchMode === option.value} onChange={() => { setMatchMode(option.value); setError(null); }} className="sr-only" />
                <span className="block text-base font-bold">{option.label}</span>
                <span className={`mt-2 block text-sm leading-5 ${matchMode === option.value ? "text-canvas/75" : "text-muted"}`}>{option.description}</span>
                <span className="mt-3 block text-xs font-bold uppercase tracking-[0.12em]" aria-hidden="true">{matchMode === option.value ? "✓ Selected" : "Choose"}</span>
              </label>
            ))}
          </div>
        </fieldset>

        <label className="flex cursor-pointer items-start justify-between gap-5 rounded-2xl border border-line bg-canvas p-4 has-focus-visible:outline-3 has-focus-visible:outline-offset-3 has-focus-visible:outline-focus">
          <span>
            <span className="block text-base font-bold text-ink">Match within the same retailer</span>
            <span className="mt-1 block text-sm leading-6 text-muted">Keep recommendations with the retailer from the original product page when available.</span>
          </span>
          <span className={`mt-1 flex h-7 w-12 shrink-0 items-center rounded-full p-1 transition-colors ${sameRetailerOnly ? "justify-end bg-ink" : "justify-start bg-line"}`} aria-hidden="true"><span className="size-5 rounded-full bg-white shadow-sm" /></span>
          <input type="checkbox" checked={sameRetailerOnly} onChange={(event) => { setSameRetailerOnly(event.target.checked); setError(null); }} className="sr-only" />
        </label>

        {submitting && <p role="status" className="rounded-xl bg-accent/55 px-4 py-3 text-sm font-semibold leading-6 text-ink">{processing ? "Comparing styles and ranking your best matches…" : "Starting your match…"}</p>}
        {error && <p role="alert" className="rounded-xl border border-clay/30 bg-blush/45 px-4 py-3 text-sm leading-6 text-ink">{error}</p>}

        <div className="flex flex-col-reverse gap-3 border-t border-line pt-6 sm:flex-row sm:justify-end">
          <button type="button" onClick={onBack} disabled={submitting} className="min-h-12 rounded-xl border border-ink/20 px-5 text-sm font-semibold text-ink hover:bg-canvas disabled:opacity-60">Back to outfit details</button>
          <button type="submit" disabled={submitting} className="min-h-12 rounded-xl bg-ink px-6 text-sm font-semibold text-canvas hover:bg-night disabled:cursor-wait disabled:opacity-65">{submitting ? "Finding matches…" : "Find partner matches"}</button>
        </div>
      </form>
    </div>
  );
}
