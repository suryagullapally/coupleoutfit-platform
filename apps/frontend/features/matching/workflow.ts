import { ApiError, NetworkError } from "../../lib/api/errors.ts";
import type { AnalysisRevision, ClothingAnalysisResponse } from "../analysis/types.ts";
import type { MatchSessionApi } from "./api.ts";
import type { CreateMatchSessionRequest, MatchSessionResponse } from "./types.ts";

export class MatchPollingTimeout extends Error {
  constructor(cause?: unknown) {
    super("Matching status timed out.", { cause });
    this.name = "MatchPollingTimeout";
  }
}

type PollOptions = {
  signal?: AbortSignal;
  attempts?: number;
  delay?: (milliseconds: number, signal?: AbortSignal) => Promise<void>;
};

function defaultDelay(milliseconds: number, signal?: AbortSignal): Promise<void> {
  return new Promise((resolve, reject) => {
    if (signal?.aborted) { reject(signal.reason); return; }
    const timer = setTimeout(resolve, milliseconds);
    signal?.addEventListener("abort", () => { clearTimeout(timer); reject(signal.reason); }, { once: true });
  });
}

export async function waitForMatchSession(api: MatchSessionApi, matchSessionId: string, options: PollOptions = {}): Promise<MatchSessionResponse> {
  const delay = options.delay ?? defaultDelay;
  const attempts = options.attempts ?? 28;
  let interval = 1_500;
  let lastTemporaryFailure: unknown;
  for (let attempt = 0; attempt < attempts; attempt += 1) {
    await delay(interval, options.signal);
    try {
      const session = await api.get(matchSessionId, options.signal);
      if (session.status === "COMPLETED" || session.status === "FAILED") return session;
      lastTemporaryFailure = undefined;
    } catch (error) {
      if (!(error instanceof NetworkError) && !(error instanceof ApiError && error.status >= 500)) throw error;
      lastTemporaryFailure = error;
    }
    interval = Math.min(5_000, Math.round(interval * 1.35));
  }
  throw new MatchPollingTimeout(lastTemporaryFailure);
}

export function isProcessing(session: MatchSessionResponse): boolean {
  return session.status === "CREATED" || session.status === "MATCHING";
}

export function eligibleAnalysisRevision(analysis: ClothingAnalysisResponse | null): AnalysisRevision | null {
  return analysis?.status === "COMPLETED" ? analysis.currentRevision : null;
}

export class MatchAttemptKeys {
  private current: { fingerprint: string; key: string } | null = null;

  keyFor(request: CreateMatchSessionRequest, createKey: () => string = () => crypto.randomUUID()): string {
    const fingerprint = JSON.stringify(request);
    if (this.current?.fingerprint !== fingerprint) this.current = { fingerprint, key: createKey() };
    return this.current.key;
  }
}

export function matchSessionPath(id: string): string {
  return `/match/${encodeURIComponent(id)}`;
}

export function matchErrorMessage(error: unknown): string {
  if (error instanceof MatchPollingTimeout) return "Finding matches is taking longer than expected. You can check again safely.";
  if (error instanceof NetworkError) return "We couldn't reach CoupleOutfit. Check your connection and try again.";
  if (error instanceof ApiError) {
    switch (error.code) {
      case "MATCH_SOURCE_NOT_READY": return "This outfit is no longer ready for matching. Return to the source step and try again.";
      case "MATCH_ANALYSIS_NOT_COMPLETED": return "The outfit analysis must finish before matching can begin.";
      case "MATCH_REVISION_NOT_CURRENT": return "The outfit details changed in another request. Refresh this page to load the latest details.";
      case "SAME_RETAILER_NOT_AVAILABLE": return "This outfit cannot currently be matched within the same retailer. Turn off that preference to continue.";
      case "MATCH_REQUEST_INVALID": return "Review your match preferences and try again.";
      case "MATCH_IDEMPOTENCY_CONFLICT": return "These preferences changed during submission. Review them and try again.";
      case "MATCH_SESSION_NOT_FOUND": return "This match is not available in this browser session.";
    }
    if (error.status >= 500) return "Matching is temporarily unavailable. Please try again later.";
  }
  return "We couldn't create this match.";
}

export function matchFailureMessage(code: string | null): string {
  if (code === "MATCH_RETRIEVAL_FAILED") return "We couldn't find suitable products right now. Please try again later.";
  if (code === "MATCH_SCORING_FAILED" || code === "MATCH_PERSISTENCE_FAILED") return "We couldn't finish ranking this match. Please try again later.";
  return "We couldn't complete this match. Please try again later.";
}
