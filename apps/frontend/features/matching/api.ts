import { createApiClient } from "../../lib/api/client.ts";
import { ApiResponseError } from "../../lib/api/errors.ts";
import { categories } from "../analysis/vocabulary.ts";
import { matchModes, matchStatuses, productTargets, type AvailabilityStatus, type CreateMatchSessionRequest, type MatchResult, type MatchSessionResponse, type ScoreBand } from "./types.ts";
const uuidPattern = /^[a-f0-9]{8}-[a-f0-9]{4}-[1-5][a-f0-9]{3}-[89ab][a-f0-9]{3}-[a-f0-9]{12}$/i;
const scoreBands: readonly ScoreBand[] = ["EXCELLENT", "GREAT", "GOOD", "POSSIBLE", "BELOW_THRESHOLD"];
const availabilityStatuses: readonly AvailabilityStatus[] = ["IN_STOCK", "OUT_OF_STOCK", "UNKNOWN"];
const purchasePathPattern = /^\/go\/[A-Za-z0-9_-]{43}$/;

function object(value: unknown): Record<string, unknown> {
  if (!value || typeof value !== "object" || Array.isArray(value)) throw new ApiResponseError();
  return value as Record<string, unknown>;
}

function text(value: unknown): string {
  if (typeof value !== "string" || !value) throw new ApiResponseError();
  return value;
}

function uuid(value: unknown): string {
  const valueText = text(value);
  if (!uuidPattern.test(valueText)) throw new ApiResponseError();
  return valueText;
}

function member<T extends string>(value: unknown, allowed: readonly T[]): T {
  if (typeof value !== "string" || !allowed.includes(value as T)) throw new ApiResponseError();
  return value as T;
}

function nullableText(value: unknown): string | null {
  return value === null ? null : text(value);
}

function integer(value: unknown, minimum: number, maximum = Number.MAX_SAFE_INTEGER): number {
  if (typeof value !== "number" || !Number.isSafeInteger(value) || value < minimum || value > maximum) throw new ApiResponseError();
  return value;
}

function imageUrl(value: unknown): string | null {
  if (value === null) return null;
  const candidate = text(value);
  let parsed: URL;
  try { parsed = new URL(candidate); } catch { throw new ApiResponseError(); }
  if (!["http:", "https:"].includes(parsed.protocol) || parsed.username || parsed.password || !parsed.hostname) throw new ApiResponseError();
  return candidate;
}

function parseResult(value: unknown, expectedRank: number): MatchResult {
  const data = object(value);
  const explanation = object(data.explanation);
  const product = object(data.product);
  const retailer = object(data.retailer);
  const offer = object(data.offer);
  const purchase = object(data.purchase);
  const rank = integer(data.rank, 1);
  const score = integer(data.coupleMatchScore, 0, 100);
  const scoreBand = member(data.scoreBand, scoreBands);
  if (rank !== expectedRank || score < 60 || scoreBand === "BELOW_THRESHOLD") throw new ApiResponseError();
  if (!Array.isArray(explanation.reasons) || explanation.reasons.length > 3 || explanation.reasons.some((reason) => typeof reason !== "string" || !reason.trim() || reason.length > 200)) throw new ApiResponseError();
  const amountMinor = offer.amountMinor === null ? null : integer(offer.amountMinor, 0);
  const currencyCode = nullableText(offer.currencyCode);
  if (amountMinor !== null && currencyCode === null) throw new ApiResponseError();
  if (currencyCode !== null && (!/^[A-Z]{3}$/.test(currencyCode) || !Intl.supportedValuesOf("currency").includes(currencyCode))) throw new ApiResponseError();
  const purchaseAvailable = boolean(purchase.available);
  const purchaseUrl = nullableText(purchase.url);
  if (purchaseAvailable ? !purchaseUrl || !purchasePathPattern.test(purchaseUrl) : purchaseUrl !== null) throw new ApiResponseError();
  return {
    resultId: uuid(data.resultId),
    rank,
    coupleMatchScore: score,
    scoreBand,
    explanation: { reasons: explanation.reasons as string[] },
    product: {
      id: uuid(product.id),
      name: text(product.name),
      brand: nullableText(product.brand),
      category: member(product.category, categories),
    },
    retailer: { id: uuid(retailer.id), slug: text(retailer.slug), name: text(retailer.name) },
    offer: {
      offerId: uuid(offer.offerId),
      amountMinor,
      currencyCode,
      availability: member(offer.availability, availabilityStatuses),
      imageUrl: imageUrl(offer.imageUrl),
    },
    purchase: { available: purchaseAvailable, url: purchaseUrl },
  };
}

export function parseMatchSessionResponse(value: unknown): MatchSessionResponse {
  const data = object(value);
  const market = object(data.market);
  const configuration = object(data.configuration);
  if (!Array.isArray(data.results)) throw new ApiResponseError();
  const results = data.results.map((result, index) => parseResult(result, index + 1));
  return {
    id: uuid(data.id),
    sourceItemId: uuid(data.sourceItemId),
    analysisRevisionId: uuid(data.analysisRevisionId),
    status: member(data.status, matchStatuses),
    market: {
      countryCode: text(market.countryCode),
      currencyCode: text(market.currencyCode),
      localeCode: text(market.localeCode),
    },
    configuration: {
      partnerTarget: member(configuration.partnerTarget, productTargets),
      matchMode: member(configuration.matchMode, matchModes),
      sameRetailerOnly: boolean(configuration.sameRetailerOnly),
    },
    algorithmVersion: text(data.algorithmVersion),
    failureCode: data.failureCode === null ? null : text(data.failureCode),
    results,
  };
}

function boolean(value: unknown): boolean {
  if (typeof value !== "boolean") throw new ApiResponseError();
  return value;
}

export interface MatchSessionApi {
  create(request: CreateMatchSessionRequest, idempotencyKey: string, signal?: AbortSignal): Promise<MatchSessionResponse>;
  get(matchSessionId: string, signal?: AbortSignal): Promise<MatchSessionResponse>;
}

export function createMatchSessionApi(origin: string, fetcher: typeof fetch = fetch): MatchSessionApi {
  const client = createApiClient(origin, fetcher, "same-origin");
  const parse = async (path: string, options?: Parameters<typeof client.request>[1]) => {
    const response = await client.request<unknown>(path, options);
    return parseMatchSessionResponse(response.data);
  };
  return {
    create: (request, idempotencyKey, signal) => parse("/api/v1/match-sessions", {
      method: "POST",
      headers: { "X-Source-Request": "1", "Idempotency-Key": idempotencyKey },
      json: request,
      signal,
    }),
    get: (id, signal) => parse(`/api/v1/match-sessions/${encodeURIComponent(id)}`, { signal }),
  };
}
