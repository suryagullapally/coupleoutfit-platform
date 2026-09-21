import { test } from "node:test";
import assert from "node:assert/strict";
import { ApiError, NetworkError } from "../lib/api/errors.ts";
import { createClothingAnalysisApi, parseAnalysisResponse, parseAttributes } from "../features/analysis/api.ts";
import type { ClothingAnalysisApi } from "../features/analysis/api.ts";
import type { ClothingAnalysisResponse, ClothingAttributes } from "../features/analysis/types.ts";
import { AnalysisPollingTimeout, analysisErrorMessage, cloneAttributes, saveCorrection, validateAttributes, waitForAnalysis } from "../features/analysis/workflow.ts";
import { tokenLabel } from "../features/analysis/vocabulary.ts";

const attributes: ClothingAttributes = {
  category: "DRESS",
  subcategory: "MIDI_DRESS",
  primaryColors: ["BURGUNDY"],
  secondaryColors: [],
  pattern: "SOLID",
  materialAppearance: ["SATIN_LIKE"],
  fit: "REGULAR",
  formality: "SMART_CASUAL",
  seasonality: ["AUTUMN", "WINTER"],
  styleTags: ["ELEGANT"],
  details: ["MIDI", "SLEEVELESS"],
};

function analysis(overrides: Partial<ClothingAnalysisResponse> = {}): ClothingAnalysisResponse {
  return {
    id: "analysis-1",
    status: "COMPLETED",
    currentRevision: {
      id: "revision-1",
      revisionNumber: 1,
      revisionType: "AI_GENERATED",
      schemaVersion: "1",
      attributes,
      confidence: { category: 0.9, primaryColors: 0.8, materialAppearance: null },
    },
    failureCode: null,
    ...overrides,
  };
}

function json(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), { status, headers: { "Content-Type": status >= 400 ? "application/problem+json" : "application/json" } });
}

test("validates the complete backend clothing schema and friendly enum labels", () => {
  assert.deepEqual(parseAttributes(attributes), attributes);
  assert.equal(tokenLabel("SMART_CASUAL"), "Smart casual");
  assert.equal(tokenLabel("T_SHIRT"), "T-shirt");
  assert.equal(tokenLabel("SATIN_LIKE"), "Satin-like");
  assert.deepEqual(parseAttributes({ ...attributes, subcategory: null, secondaryColors: [], materialAppearance: [], seasonality: [], styleTags: [], details: [] }).subcategory, null);
  assert.throws(() => parseAttributes({ ...attributes, unapproved: "value" }));
  assert.throws(() => parseAttributes({ ...attributes, primaryColors: ["BURGUNDY", "BURGUNDY"] }));
});

test("uses the exact start, retrieval and immutable correction contracts", async () => {
  const requests: Array<{ path: string; method: string; credentials?: RequestCredentials; header: string | null; body?: BodyInit | null }> = [];
  const fetcher: typeof fetch = async (input, init) => {
    requests.push({
      path: new URL(String(input)).pathname,
      method: init?.method ?? "GET",
      credentials: init?.credentials,
      header: new Headers(init?.headers).get("X-Source-Request"),
      body: init?.body,
    });
    return json(analysis());
  };
  const api = createClothingAnalysisApi("https://coupleoutfit.example", fetcher);
  await api.start("source-1");
  await api.get("source-1");
  await api.correct("source-1", "revision-1", attributes);
  assert.deepEqual(requests, [
    { path: "/api/v1/source-items/source-1/analysis", method: "POST", credentials: "same-origin", header: "1", body: undefined },
    { path: "/api/v1/source-items/source-1/analysis", method: "GET", credentials: "same-origin", header: null, body: undefined },
    {
      path: "/api/v1/source-items/source-1/analysis/revisions",
      method: "POST",
      credentials: "same-origin",
      header: "1",
      body: JSON.stringify({ parentRevisionId: "revision-1", attributes }),
    },
  ]);
});

test("accepts all implemented analysis states and corrected revisions", () => {
  for (const status of ["PENDING", "IN_PROGRESS", "COMPLETED", "FAILED"] as const) {
    const value = status === "COMPLETED" ? analysis() : analysis({ status, currentRevision: null, failureCode: status === "FAILED" ? "ANALYSIS_TIMEOUT" : null });
    assert.equal(parseAnalysisResponse(value).status, status);
  }
  const corrected = analysis({ currentRevision: { ...analysis().currentRevision!, id: "revision-2", revisionNumber: 2, revisionType: "USER_CORRECTED", confidence: null } });
  assert.equal(parseAnalysisResponse(corrected).currentRevision?.revisionType, "USER_CORRECTED");
});

test("validates correction list bounds and primary/secondary colour separation", () => {
  assert.deepEqual(validateAttributes(attributes), {});
  assert.match(validateAttributes({ ...attributes, primaryColors: ["RED", "BLUE", "GREEN", "BLACK"] }).primaryColors ?? "", /3/);
  assert.match(validateAttributes({ ...attributes, secondaryColors: ["BURGUNDY"] }).secondaryColors ?? "", /different/);
  assert.match(validateAttributes({ ...attributes, styleTags: Array(9).fill("ELEGANT") }).styleTags ?? "", /8/);
  const clone = cloneAttributes(attributes);
  assert.notEqual(clone.primaryColors, attributes.primaryColors);
});

test("polls an already-running analysis and stops on completion", async () => {
  const states = [analysis({ status: "IN_PROGRESS", currentRevision: null }), analysis()];
  let gets = 0;
  const api = stubApi(async () => { gets += 1; return states.shift() ?? analysis(); });
  const result = await waitForAnalysis(api, "source-1", { delay: async () => {}, attempts: 5 });
  assert.equal(result.status, "COMPLETED");
  assert.equal(gets, 2);
});

test("polling stops on terminal failure and tolerates one temporary network failure", async () => {
  let gets = 0;
  const api = stubApi(async () => {
    gets += 1;
    if (gets === 1) throw new NetworkError();
    return analysis({ status: "FAILED", currentRevision: null, failureCode: "ANALYSIS_TIMEOUT" });
  });
  const result = await waitForAnalysis(api, "source-1", { delay: async () => {}, attempts: 3 });
  assert.equal(result.status, "FAILED");
  assert.equal(gets, 2);
});

test("polling has a bounded timeout", async () => {
  const api = stubApi(async () => analysis({ status: "IN_PROGRESS", currentRevision: null }));
  await assert.rejects(waitForAnalysis(api, "source-1", { delay: async () => {}, attempts: 2 }), AnalysisPollingTimeout);
});

test("polling stops when navigation or component cleanup aborts it", async () => {
  const controller = new AbortController();
  const api = stubApi(async () => analysis({ status: "IN_PROGRESS", currentRevision: null }));
  const pending = waitForAnalysis(api, "source-1", {
    signal: controller.signal,
    delay: async (_milliseconds, signal) => {
      controller.abort(new DOMException("The operation was aborted.", "AbortError"));
      if (signal?.aborted) throw signal.reason;
    },
  });
  await assert.rejects(pending, (error: unknown) => error instanceof DOMException && error.name === "AbortError");
});

test("successful corrections replace the authoritative immutable revision", async () => {
  const original = analysis();
  const corrected = analysis({ currentRevision: { ...original.currentRevision!, id: "revision-2", revisionNumber: 2, revisionType: "USER_CORRECTED", attributes: { ...attributes, primaryColors: ["BLUE"] }, confidence: null } });
  const api: ClothingAnalysisApi = {
    async correct(_source, parent, payload) { assert.equal(parent, "revision-1"); assert.deepEqual(payload.primaryColors, ["BLUE"]); return corrected; },
    async start() { throw new Error("unused"); },
    async get() { throw new Error("unused"); },
  };
  const result = await saveCorrection(api, "source-1", "revision-1", { ...attributes, primaryColors: ["BLUE"] });
  assert.equal(result.kind, "saved");
  assert.equal(result.analysis.currentRevision?.id, "revision-2");
  assert.equal(original.currentRevision?.attributes.primaryColors[0], "BURGUNDY");
});

test("a stale correction retrieves the latest revision instead of overwriting it", async () => {
  const latest = analysis({ currentRevision: { ...analysis().currentRevision!, id: "revision-2", revisionNumber: 2, revisionType: "USER_CORRECTED", confidence: null } });
  const api: ClothingAnalysisApi = {
    async correct() { throw new ApiError(409, "trace-1", "ANALYSIS_REVISION_CONFLICT"); },
    async get() { return latest; },
    async start() { throw new Error("unused"); },
  };
  const result = await saveCorrection(api, "source-1", "revision-1", attributes);
  assert.equal(result.kind, "conflict");
  assert.equal(result.analysis.currentRevision?.id, "revision-2");
});

test("network correction failure remains retryable without mutating the draft", async () => {
  const draft = cloneAttributes({ ...attributes, primaryColors: ["BLUE"] });
  const api: ClothingAnalysisApi = {
    async correct() { throw new NetworkError(); },
    async get() { throw new Error("unused"); },
    async start() { throw new Error("unused"); },
  };
  await assert.rejects(saveCorrection(api, "source-1", "revision-1", draft), NetworkError);
  assert.deepEqual(draft.primaryColors, ["BLUE"]);
});

test("maps unsupported, conflict and temporary analysis failures distinctly", () => {
  assert.match(analysisErrorMessage(new ApiError(422, undefined, "ANALYSIS_IMAGE_UNSUPPORTED")), /image/);
  assert.match(analysisErrorMessage(new ApiError(409, undefined, "ANALYSIS_REVISION_CONFLICT")), /newer/);
  assert.match(analysisErrorMessage(new ApiError(503, undefined, "ANALYSIS_PROVIDER_UNAVAILABLE")), /temporarily/);
});

function stubApi(get: ClothingAnalysisApi["get"]): ClothingAnalysisApi {
  return {
    get,
    async start() { throw new Error("unused"); },
    async correct() { throw new Error("unused"); },
  };
}
