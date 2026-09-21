import { readFile } from "node:fs/promises";
import { test } from "node:test";
import assert from "node:assert/strict";
import { ApiError, NetworkError } from "../lib/api/errors.ts";
import { createMatchSessionApi, parseMatchSessionResponse } from "../features/matching/api.ts";
import type { MatchSessionApi } from "../features/matching/api.ts";
import type { ClothingAnalysisResponse } from "../features/analysis/types.ts";
import type { CreateMatchSessionRequest, MatchSessionResponse } from "../features/matching/types.ts";
import { DEFAULT_MATCH_MODE, matchModes, productTargets } from "../features/matching/types.ts";
import { eligibleAnalysisRevision, isProcessing, MatchAttemptKeys, matchErrorMessage, matchSessionPath, MatchPollingTimeout, waitForMatchSession } from "../features/matching/workflow.ts";
import { availabilityLabel, offerPrice, scoreBandLabel } from "../features/matching/presentation.ts";
import { SubmissionGate } from "../features/source/workflow.ts";

const sourceId = "11111111-1111-4111-8111-111111111111";
const revisionId = "22222222-2222-4222-8222-222222222222";
const sessionId = "33333333-3333-4333-8333-333333333333";
const key = "44444444-4444-4444-8444-444444444444";
const tokenPath = `/go/${"A".repeat(43)}`;

const request: CreateMatchSessionRequest = {
  sourceItemId: sourceId,
  analysisRevisionId: revisionId,
  partnerTarget: "MENSWEAR",
  matchMode: "COORDINATED",
  sameRetailerOnly: false,
};

function response(overrides: Partial<Record<string, unknown>> = {}): Record<string, unknown> {
  return {
    id: sessionId,
    sourceItemId: sourceId,
    analysisRevisionId: revisionId,
    status: "COMPLETED",
    market: { countryCode: "GB", currencyCode: "GBP", localeCode: "en-GB" },
    configuration: { partnerTarget: "MENSWEAR", matchMode: "COORDINATED", sameRetailerOnly: false },
    algorithmVersion: "matching-v1",
    failureCode: null,
    results: [],
    ...overrides,
  };
}

function result(rank = 1, overrides: Partial<Record<string, unknown>> = {}): Record<string, unknown> {
  return {
    resultId: `${rank}aaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa`,
    rank,
    coupleMatchScore: rank === 1 ? 92 : 84,
    scoreBand: rank === 1 ? "EXCELLENT" : "GREAT",
    explanation: { reasons: ["The colors coordinate naturally.", "The pieces share a similar style or style family."] },
    product: { id: `${rank}bbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb`, name: `Partner look ${rank}`, brand: "Fixture brand", category: "DRESS" },
    retailer: { id: `${rank}ccccccc-cccc-4ccc-8ccc-cccccccccccc`, slug: "fixture-retailer", name: "Fixture Retailer" },
    offer: { offerId: `${rank}ddddddd-dddd-4ddd-8ddd-dddddddddddd`, amountMinor: 4999, currencyCode: "GBP", availability: "IN_STOCK", imageUrl: "https://images.example/look.jpg" },
    purchase: { available: true, url: tokenPath },
    ...overrides,
  };
}

function json(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), { status, headers: { "Content-Type": status >= 400 ? "application/problem+json" : "application/json" } });
}

test("uses the backend target and mode vocabulary with Coordinated as the UI default", () => {
  assert.deepEqual(productTargets, ["MENSWEAR", "WOMENSWEAR"]);
  assert.deepEqual(matchModes, ["EXACT", "COORDINATED", "COMPLEMENTARY"]);
  assert.equal(DEFAULT_MATCH_MODE, "COORDINATED");
});

test("parses every MatchSession status and accepts completed zero-result sessions", () => {
  for (const status of ["CREATED", "MATCHING", "COMPLETED", "FAILED"] as const) {
    const parsed = parseMatchSessionResponse(response({ status, failureCode: status === "FAILED" ? "MATCH_RETRIEVAL_FAILED" : null }));
    assert.equal(parsed.status, status);
    assert.equal(parsed.results.length, 0);
    assert.equal(isProcessing(parsed), status === "CREATED" || status === "MATCHING");
  }
  assert.throws(() => parseMatchSessionResponse(response({ status: "READY" })));
});

test("validates the complete production result contract and preserves backend rank order", () => {
  const parsed = parseMatchSessionResponse(response({ results: [result(1), result(2)] }));
  assert.deepEqual(parsed.results.map((item) => item.product.name), ["Partner look 1", "Partner look 2"]);
  assert.deepEqual(parsed.results.map((item) => item.coupleMatchScore), [92, 84]);
  assert.deepEqual(parsed.results[0]?.explanation.reasons, ["The colors coordinate naturally.", "The pieces share a similar style or style family."]);
  assert.equal(parsed.results[0]?.purchase.url, tokenPath);
});

test("supports missing optional commercial fields and rejects malformed or hidden results", () => {
  const optional = parseMatchSessionResponse(response({ results: [result(1, {
    offer: { offerId: "1ddddddd-dddd-4ddd-8ddd-dddddddddddd", amountMinor: null, currencyCode: null, availability: "UNKNOWN", imageUrl: null },
    purchase: { available: false, url: null },
    explanation: { reasons: [] },
    product: { id: "1bbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb", name: "Partner look 1", brand: null, category: "DRESS" },
  })] }));
  assert.equal(optional.results[0]?.offer.amountMinor, null);
  assert.equal(optional.results[0]?.offer.imageUrl, null);
  assert.equal(optional.results[0]?.purchase.available, false);
  assert.equal(optional.results[0]?.product.brand, null);

  assert.throws(() => parseMatchSessionResponse(response({ results: [result(1, { coupleMatchScore: 59, scoreBand: "BELOW_THRESHOLD" })] })));
  assert.throws(() => parseMatchSessionResponse(response({ results: [result(1, { rank: 2 })] })));
  assert.throws(() => parseMatchSessionResponse(response({ results: [result(1, { purchase: { available: true, url: "https://retailer.example/product" } })] })));
  assert.throws(() => parseMatchSessionResponse(response({ results: [result(1, { offer: { offerId: "1ddddddd-dddd-4ddd-8ddd-dddddddddddd", amountMinor: 10, currencyCode: null, availability: "IN_STOCK", imageUrl: "javascript:alert(1)" } })] })));
  assert.throws(() => parseMatchSessionResponse(response({ results: [result(1, { explanation: { reasons: [""] } })] })));
});

test("presents authoritative bands, availability and minor-unit prices without hard-coded symbols", () => {
  assert.equal(scoreBandLabel("EXCELLENT"), "Excellent match");
  assert.equal(scoreBandLabel("GREAT"), "Great match");
  assert.equal(availabilityLabel("IN_STOCK"), "In stock");
  assert.equal(availabilityLabel("UNKNOWN"), "Check availability");
  assert.equal(offerPrice(4999, "GBP", "en-GB"), "£49.99");
  assert.equal(offerPrice(null, null, "en-GB"), null);
  assert.equal(offerPrice(5000, "JPY", "ja-JP"), "￥5,000");
});

test("creates and retrieves MatchSessions with exact headers, credentials and request fields", async () => {
  const seen: Array<{ path: string; method: string; credentials?: RequestCredentials; sourceHeader: string | null; keyHeader: string | null; body?: BodyInit | null }> = [];
  const api = createMatchSessionApi("https://coupleoutfit.example", async (input, init) => {
    seen.push({
      path: new URL(String(input)).pathname,
      method: init?.method ?? "GET",
      credentials: init?.credentials,
      sourceHeader: new Headers(init?.headers).get("X-Source-Request"),
      keyHeader: new Headers(init?.headers).get("Idempotency-Key"),
      body: init?.body,
    });
    return json(response());
  });
  await api.create(request, key);
  await api.get(sessionId);
  assert.deepEqual(seen, [
    { path: "/api/v1/match-sessions", method: "POST", credentials: "same-origin", sourceHeader: "1", keyHeader: key, body: JSON.stringify(request) },
    { path: `/api/v1/match-sessions/${sessionId}`, method: "GET", credentials: "same-origin", sourceHeader: null, keyHeader: null, body: undefined },
  ]);
  assert.equal(Object.hasOwn(request, "market"), false);
});

test("uses only a completed authoritative current revision for matching eligibility", () => {
  const revision = {
    id: revisionId,
    revisionNumber: 2,
    revisionType: "USER_CORRECTED" as const,
    schemaVersion: "1",
    attributes: { category: "DRESS" as const, subcategory: null, primaryColors: [], secondaryColors: [], pattern: "SOLID" as const, materialAppearance: [], fit: "REGULAR" as const, formality: "SMART_CASUAL" as const, seasonality: [], styleTags: [], details: [] },
    confidence: null,
  };
  const completed: ClothingAnalysisResponse = { id: "analysis", status: "COMPLETED", currentRevision: revision, failureCode: null };
  assert.equal(eligibleAnalysisRevision(completed)?.id, revisionId);
  assert.equal(eligibleAnalysisRevision({ ...completed, status: "IN_PROGRESS", currentRevision: null }), null);
  assert.equal(eligibleAnalysisRevision({ ...completed, status: "FAILED", currentRevision: null }), null);
});

test("polls only processing sessions and stops on completion", async () => {
  const states = [session({ status: "MATCHING" }), session({ status: "COMPLETED" })];
  let gets = 0;
  const api = stubApi(async () => { gets += 1; return states.shift() as MatchSessionResponse; });
  const final = await waitForMatchSession(api, sessionId, { attempts: 4, delay: async () => {} });
  assert.equal(final.status, "COMPLETED");
  assert.equal(gets, 2);
});

test("polling handles temporary failure, terminal failure, timeout and cancellation", async () => {
  let calls = 0;
  const temporary = stubApi(async () => {
    calls += 1;
    if (calls === 1) throw new NetworkError();
    return session({ status: "FAILED", failureCode: "MATCH_RETRIEVAL_FAILED" });
  });
  assert.equal((await waitForMatchSession(temporary, sessionId, { attempts: 3, delay: async () => {} })).status, "FAILED");
  await assert.rejects(waitForMatchSession(stubApi(async () => session({ status: "MATCHING" })), sessionId, { attempts: 2, delay: async () => {} }), MatchPollingTimeout);

  const controller = new AbortController();
  const cancelled = waitForMatchSession(stubApi(async () => session({ status: "MATCHING" })), sessionId, {
    signal: controller.signal,
    delay: async (_milliseconds, signal) => {
      controller.abort(new DOMException("Aborted", "AbortError"));
      if (signal?.aborted) throw signal.reason;
    },
  });
  await assert.rejects(cancelled, (error: unknown) => error instanceof DOMException && error.name === "AbortError");
});

test("prevents duplicate creation while preserving a safe same-request retry key", () => {
  const gate = new SubmissionGate();
  assert.equal(gate.tryEnter(), true);
  assert.equal(gate.tryEnter(), false);
  gate.leave();
  assert.equal(gate.tryEnter(), true);
  let sequence = 0;
  const keys = new MatchAttemptKeys();
  const createKey = () => `key-${++sequence}`;
  assert.equal(keys.keyFor(request, createKey), "key-1");
  assert.equal(keys.keyFor({ ...request }, createKey), "key-1");
  assert.equal(keys.keyFor({ ...request, sameRetailerOnly: true }, createKey), "key-2");
});

test("maps matching contract failures without exposing backend details", () => {
  assert.match(matchErrorMessage(new ApiError(422, "trace", "SAME_RETAILER_NOT_AVAILABLE")), /same retailer/i);
  assert.match(matchErrorMessage(new ApiError(409, "trace", "MATCH_REVISION_NOT_CURRENT")), /changed/i);
  assert.match(matchErrorMessage(new ApiError(404, "trace", "MATCH_SESSION_NOT_FOUND")), /browser session/i);
  assert.match(matchErrorMessage(new ApiError(500, "trace", "MATCH_INTERNAL_SECRET")), /temporarily/i);
  assert.equal(matchSessionPath(sessionId), `/match/${sessionId}`);
});

test("preference controls and canonical route retain native semantics and noindex metadata", async () => {
  const component = await readFile(new URL("../features/matching/match-preferences.tsx", import.meta.url), "utf8");
  const route = await readFile(new URL("../app/match/[matchSessionId]/page.tsx", import.meta.url), "utf8");
  assert.match(component, /type="radio" name="partnerTarget"/);
  assert.match(component, /type="radio" name="matchMode"/);
  assert.match(component, /type="checkbox"/);
  assert.match(component, /sameRetailerOnly/);
  assert.match(route, /robots: \{ index: false, follow: true \}/);
});

test("results UI uses backend order, textual scores, safe purchase paths and no extra click request", async () => {
  const component = await readFile(new URL("../features/matching/match-results.tsx", import.meta.url), "utf8");
  const config = await readFile(new URL("../next.config.ts", import.meta.url), "utf8");
  assert.match(component, /session\.results\.map/);
  assert.match(component, /result\.coupleMatchScore/);
  assert.match(component, /result\.explanation\.reasons\.map/);
  assert.match(component, /href=\{result\.purchase\.url\}/);
  assert.match(component, /aria-label=\{`View \$\{result\.product\.name\} at \$\{result\.retailer\.name\}`\}/);
  assert.match(component, /Product image unavailable/);
  assert.match(component, /No strong matches yet/);
  assert.doesNotMatch(component, /fetch\(/);
  assert.match(config, /source: "\/go\/:token"/);
});

function session(overrides: Partial<MatchSessionResponse> = {}): MatchSessionResponse {
  return { ...parseMatchSessionResponse(response()), ...overrides };
}

function stubApi(get: MatchSessionApi["get"]): MatchSessionApi {
  return { get, async create() { throw new Error("unused"); } };
}
