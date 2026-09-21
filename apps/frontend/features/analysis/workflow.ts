import { ApiError, NetworkError } from "../../lib/api/errors.ts";
import type { ClothingAnalysisApi } from "./api.ts";
import type { ClothingAnalysisResponse, ClothingAttributes } from "./types.ts";
import { colors, details, materials, seasons, styles } from "./vocabulary.ts";

export type AttributeErrors = Partial<Record<keyof ClothingAttributes, string>>;

export function cloneAttributes(attributes: ClothingAttributes): ClothingAttributes {
  return {
    ...attributes,
    primaryColors: [...attributes.primaryColors],
    secondaryColors: [...attributes.secondaryColors],
    materialAppearance: [...attributes.materialAppearance],
    seasonality: [...attributes.seasonality],
    styleTags: [...attributes.styleTags],
    details: [...attributes.details],
  };
}

export function validateAttributes(attributes: ClothingAttributes): AttributeErrors {
  const errors: AttributeErrors = {};
  const check = (field: keyof ClothingAttributes, values: readonly string[], allowed: readonly string[], maximum: number) => {
    if (values.length > maximum) errors[field] = `Choose no more than ${maximum}.`;
    else if (new Set(values).size !== values.length || values.some((value) => !allowed.includes(value))) errors[field] = "Review these selections.";
  };
  check("primaryColors", attributes.primaryColors, colors, 3);
  check("secondaryColors", attributes.secondaryColors, colors, 3);
  if (attributes.primaryColors.some((color) => attributes.secondaryColors.includes(color))) {
    errors.secondaryColors = "Primary and secondary colours must be different.";
  }
  check("materialAppearance", attributes.materialAppearance, materials, 4);
  check("seasonality", attributes.seasonality, seasons, 4);
  check("styleTags", attributes.styleTags, styles, 8);
  check("details", attributes.details, details, 10);
  return errors;
}

export class AnalysisPollingTimeout extends Error {
  constructor(cause?: unknown) { super("Analysis status timed out.", { cause }); this.name = "AnalysisPollingTimeout"; }
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

export async function waitForAnalysis(api: ClothingAnalysisApi, sourceItemId: string, options: PollOptions = {}): Promise<ClothingAnalysisResponse> {
  const attempts = options.attempts ?? 28;
  const delay = options.delay ?? defaultDelay;
  let interval = 1_500;
  let lastTemporaryFailure: unknown;
  for (let attempt = 0; attempt < attempts; attempt += 1) {
    await delay(interval, options.signal);
    try {
      const analysis = await api.get(sourceItemId, options.signal);
      if (analysis.status === "COMPLETED" || analysis.status === "FAILED") return analysis;
      lastTemporaryFailure = undefined;
    } catch (error) {
      if (!(error instanceof NetworkError) && !(error instanceof ApiError && error.status >= 500)) throw error;
      lastTemporaryFailure = error;
    }
    interval = Math.min(5_000, Math.round(interval * 1.35));
  }
  throw new AnalysisPollingTimeout(lastTemporaryFailure);
}

export type CorrectionResult =
  | { readonly kind: "saved"; readonly analysis: ClothingAnalysisResponse }
  | { readonly kind: "conflict"; readonly analysis: ClothingAnalysisResponse };

export async function saveCorrection(
  api: ClothingAnalysisApi,
  sourceItemId: string,
  parentRevisionId: string,
  attributes: ClothingAttributes,
): Promise<CorrectionResult> {
  try {
    return { kind: "saved", analysis: await api.correct(sourceItemId, parentRevisionId, attributes) };
  } catch (error) {
    if (error instanceof ApiError && error.code === "ANALYSIS_REVISION_CONFLICT") {
      return { kind: "conflict", analysis: await api.get(sourceItemId) };
    }
    throw error;
  }
}

export function analysisErrorMessage(error: unknown): string {
  if (error instanceof AnalysisPollingTimeout) return "Analysis is taking longer than expected. You can check again safely.";
  if (error instanceof NetworkError) return "We couldn't reach CoupleOutfit. Check your connection and try again.";
  if (error instanceof ApiError) {
    switch (error.code) {
      case "ANALYSIS_NOT_FOUND": return "This outfit is ready to be analysed.";
      case "ANALYSIS_SOURCE_NOT_READY": return "This outfit is not ready for analysis. Return to the source step and try again.";
      case "ANALYSIS_IMAGE_UNSUPPORTED": return "We couldn't read enough from that outfit image. Try another image.";
      case "ANALYSIS_INVALID_ATTRIBUTES": return "Some outfit details need attention before they can be saved.";
      case "ANALYSIS_REVISION_CONFLICT": return "A newer review was saved. The latest details have been loaded.";
      case "SOURCE_NOT_FOUND": return "This outfit is no longer available in this browser session.";
    }
    if (error.status >= 500) return "Outfit analysis is temporarily unavailable. Please try again later.";
  }
  return "We couldn't complete the outfit analysis.";
}
