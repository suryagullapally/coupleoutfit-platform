import { createApiClient } from "../../lib/api/client.ts";
import { ApiResponseError } from "../../lib/api/errors.ts";
import { categories, colors, details, fits, formalities, materials, patterns, seasons, styles, subcategories } from "./vocabulary.ts";
import type { AnalysisStatus, ClothingAnalysisResponse, ClothingAttributes, RevisionType } from "./types.ts";

const statuses: readonly AnalysisStatus[] = ["PENDING", "IN_PROGRESS", "COMPLETED", "FAILED"];
const revisionTypes: readonly RevisionType[] = ["AI_GENERATED", "USER_CORRECTED"];

function object(value: unknown): Record<string, unknown> {
  if (!value || typeof value !== "object" || Array.isArray(value)) throw new ApiResponseError();
  return value as Record<string, unknown>;
}
function text(value: unknown): string {
  if (typeof value !== "string" || !value) throw new ApiResponseError();
  return value;
}
function nullableText(value: unknown): string | null { return value === null ? null : text(value); }
function integer(value: unknown): number {
  if (typeof value !== "number" || !Number.isInteger(value) || value < 1) throw new ApiResponseError();
  return value;
}
function member<T extends string>(value: unknown, allowed: readonly T[]): T {
  if (typeof value !== "string" || !allowed.includes(value as T)) throw new ApiResponseError();
  return value as T;
}
function tokens(value: unknown, allowed: readonly string[], maximum: number): string[] {
  if (!Array.isArray(value) || value.length > maximum || new Set(value).size !== value.length || value.some((item) => typeof item !== "string" || !allowed.includes(item))) throw new ApiResponseError();
  return value as string[];
}
function confidence(value: unknown): number | null {
  if (value === null) return null;
  if (typeof value !== "number" || !Number.isFinite(value) || value < 0 || value > 1) throw new ApiResponseError();
  return value;
}

export function parseAttributes(value: unknown): ClothingAttributes {
  const data = object(value);
  const keys = Object.keys(data).sort();
  const expected = ["category", "subcategory", "primaryColors", "secondaryColors", "pattern", "materialAppearance", "fit", "formality", "seasonality", "styleTags", "details"].sort();
  if (keys.length !== expected.length || keys.some((key, index) => key !== expected[index])) throw new ApiResponseError();
  const primaryColors = tokens(data.primaryColors, colors, 3);
  const secondaryColors = tokens(data.secondaryColors, colors, 3);
  if (primaryColors.some((color) => secondaryColors.includes(color))) throw new ApiResponseError();
  return {
    category: member(data.category, categories),
    subcategory: data.subcategory === null ? null : member(data.subcategory, subcategories),
    primaryColors,
    secondaryColors,
    pattern: member(data.pattern, patterns),
    materialAppearance: tokens(data.materialAppearance, materials, 4),
    fit: member(data.fit, fits),
    formality: member(data.formality, formalities),
    seasonality: tokens(data.seasonality, seasons, 4),
    styleTags: tokens(data.styleTags, styles, 8),
    details: tokens(data.details, details, 10),
  };
}

export function parseAnalysisResponse(value: unknown): ClothingAnalysisResponse {
  const data = object(value);
  const revision = data.currentRevision === null ? null : object(data.currentRevision);
  const confidenceData = revision?.confidence === null ? null : revision ? object(revision.confidence) : null;
  return {
    id: text(data.id),
    status: member(data.status, statuses),
    currentRevision: revision && {
      id: text(revision.id),
      revisionNumber: integer(revision.revisionNumber),
      revisionType: member(revision.revisionType, revisionTypes),
      schemaVersion: text(revision.schemaVersion),
      attributes: parseAttributes(revision.attributes),
      confidence: confidenceData && {
        category: confidence(confidenceData.category),
        primaryColors: confidence(confidenceData.primaryColors),
        materialAppearance: confidence(confidenceData.materialAppearance),
      },
    },
    failureCode: nullableText(data.failureCode),
  };
}

export interface ClothingAnalysisApi {
  start(sourceItemId: string, signal?: AbortSignal): Promise<ClothingAnalysisResponse>;
  get(sourceItemId: string, signal?: AbortSignal): Promise<ClothingAnalysisResponse>;
  correct(sourceItemId: string, parentRevisionId: string, attributes: ClothingAttributes): Promise<ClothingAnalysisResponse>;
}

export function createClothingAnalysisApi(origin: string, fetcher: typeof fetch = fetch): ClothingAnalysisApi {
  const client = createApiClient(origin, fetcher, "same-origin");
  const path = (sourceItemId: string) => `/api/v1/source-items/${encodeURIComponent(sourceItemId)}/analysis`;
  const request = async (url: string, options?: Parameters<typeof client.request>[1]) => {
    const result = await client.request<unknown>(url, options);
    return parseAnalysisResponse(result.data);
  };
  return {
    start: (id, signal) => request(path(id), { method: "POST", headers: { "X-Source-Request": "1" }, signal }),
    get: (id, signal) => request(path(id), { signal }),
    correct: (id, parentRevisionId, attributes) => request(`${path(id)}/revisions`, {
      method: "POST",
      headers: { "X-Source-Request": "1" },
      json: { parentRevisionId, attributes },
    }),
  };
}
