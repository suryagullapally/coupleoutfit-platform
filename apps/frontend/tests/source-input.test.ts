import { test } from "node:test";
import assert from "node:assert/strict";
import { ApiError, NetworkError } from "../lib/api/errors.ts";
import { createSourceItemApi, parseSourceItemResponse } from "../features/source/api.ts";
import type { SourceItemApi } from "../features/source/api.ts";
import type { SourceItemResponse, SourceUploadAuthorization } from "../features/source/types.ts";
import { SOURCE_IMAGE_MAX_BYTES, validateProductUrl, validateSourceImage } from "../features/source/validation.ts";
import { ImageWorkflowError, SubmissionGate, sourceErrorMessage, uploadImageSource } from "../features/source/workflow.ts";

const authorization: SourceUploadAuthorization = {
  assetId: "asset-1",
  uploadUrl: "/internal/local-upload?token=temporary",
  expiresAt: "2026-09-21T12:00:00Z",
  method: "PUT",
};

function source(overrides: Partial<SourceItemResponse> = {}): SourceItemResponse {
  return {
    sourceItemId: "source-1",
    sourceType: "IMAGE_UPLOAD",
    status: "AWAITING_UPLOAD",
    market: { countryCode: "GB", currencyCode: "GBP", localeCode: "en-GB" },
    asset: { assetId: "asset-1", status: "PENDING", mediaType: null, width: null, height: null },
    urlResolution: null,
    upload: authorization,
    action: null,
    ...overrides,
  };
}

function json(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), { status, headers: { "Content-Type": status >= 400 ? "application/problem+json" : "application/json" } });
}

test("validates obvious product URL mistakes without reproducing backend resolution", () => {
  assert.equal(validateProductUrl("https://shop.example/product"), null);
  assert.equal(validateProductUrl(""), "Enter a product link to continue.");
  assert.match(validateProductUrl("shop.example/product") ?? "", /http/);
  assert.match(validateProductUrl("javascript:alert(1)") ?? "", /http/);
});

test("validates the backend image media types and ten-megabyte limit", () => {
  assert.equal(validateSourceImage({ type: "image/png", size: SOURCE_IMAGE_MAX_BYTES }), null);
  assert.match(validateSourceImage({ type: "image/gif", size: 20 }) ?? "", /JPG, PNG or WebP/);
  assert.match(validateSourceImage({ type: "image/jpeg", size: SOURCE_IMAGE_MAX_BYTES + 1 }) ?? "", /10 MB/);
  assert.match(validateSourceImage({ type: "image/webp", size: 0 }) ?? "", /not empty/);
});

test("submits the exact product URL contract through the credentialed same-origin proxy", async () => {
  let calls = 0;
  const fetcher: typeof fetch = async (input, init) => {
    calls += 1;
    assert.equal(String(input), "https://coupleoutfit.example/api/v1/source-items");
    assert.equal(init?.credentials, "same-origin");
    assert.equal(init?.method, "POST");
    assert.equal(new Headers(init?.headers).get("X-Source-Request"), "1");
    assert.equal(init?.body, '{"sourceType":"PRODUCT_URL","url":"https://unknown.example/look"}');
    return json(source({
      sourceType: "PRODUCT_URL",
      status: "IMAGE_UPLOAD_REQUIRED",
      asset: null,
      upload: null,
      action: "UPLOAD_IMAGE",
      urlResolution: { status: "UNSUPPORTED", retailerMarketId: null, productOfferId: null, reason: "SOURCE_URL_UNSUPPORTED" },
    }), 201);
  };
  const result = await createSourceItemApi("https://coupleoutfit.example", fetcher).createFromUrl("https://unknown.example/look");
  assert.equal(calls, 1);
  assert.equal(result.status, "IMAGE_UPLOAD_REQUIRED");
  assert.equal(result.action, "UPLOAD_IMAGE");
});

test("preserves backend validation codes without exposing backend detail", async () => {
  const api = createSourceItemApi("https://coupleoutfit.example", async () => json({ code: "SOURCE_URL_INVALID", detail: "private detail" }, 400));
  await assert.rejects(api.createFromUrl("https://example.com"), (error: unknown) => {
    assert.ok(error instanceof ApiError);
    assert.equal(error.code, "SOURCE_URL_INVALID");
    assert.ok(!error.message.includes("private"));
    return true;
  });
});

test("uses the implemented image authorization, completion and owned-status contracts", async () => {
  const seen: Array<{ path: string; method: string; body?: BodyInit | null }> = [];
  const fetcher: typeof fetch = async (input, init) => {
    const path = new URL(String(input)).pathname;
    seen.push({ path, method: init?.method ?? "GET", body: init?.body });
    if (path.endsWith("/upload-complete")) return json(source({ status: "READY_FOR_ANALYSIS", upload: null }), 200);
    return json(source(), path === "/api/v1/source-items" ? 201 : 200);
  };
  const api = createSourceItemApi("https://coupleoutfit.example", fetcher);
  await api.createFromImage();
  await api.authorizeUpload("source-1");
  await api.completeUpload("source-1", "asset-1");
  await api.get("source-1");
  assert.deepEqual(seen, [
    { path: "/api/v1/source-items", method: "POST", body: '{"sourceType":"IMAGE_UPLOAD"}' },
    { path: "/api/v1/source-items/source-1/upload-authorization", method: "POST", body: undefined },
    { path: "/api/v1/source-items/source-1/upload-complete", method: "POST", body: '{"assetId":"asset-1"}' },
    { path: "/api/v1/source-items/source-1", method: "GET", body: undefined },
  ]);
});

test("accepts every implemented SourceItem status and rejects invented states", () => {
  for (const status of ["AWAITING_UPLOAD", "UPLOAD_VERIFYING", "RESOLVING_URL", "READY_FOR_ANALYSIS", "IMAGE_UPLOAD_REQUIRED", "FAILED"] as const) {
    assert.equal(parseSourceItemResponse(source({ status })).status, status);
  }
  assert.throws(() => parseSourceItemResponse(source({ status: "COMPLETE" as never })));
});

test("uploads bytes with the authorized PUT and without cross-origin credentials", async () => {
  const requests: Array<{ url: string; credentials?: RequestCredentials }> = [];
  const api = createSourceItemApi("https://coupleoutfit.example", async (input, init) => {
    requests.push({ url: String(input), credentials: init?.credentials });
    return new Response(null, { status: 204 });
  });
  await api.uploadFile({ ...authorization, uploadUrl: "https://storage.example/upload?signature=secret" }, new Blob(["image"], { type: "image/png" }));
  assert.deepEqual(requests, [{ url: "https://storage.example/upload?signature=secret", credentials: "omit" }]);
});

test("reports upload transport failure and safe completion failure", async () => {
  const networkApi = createSourceItemApi("https://coupleoutfit.example", async () => { throw new TypeError("private network detail"); });
  await assert.rejects(networkApi.uploadFile(authorization, new Blob(["x"], { type: "image/png" })), NetworkError);

  const failingApi = createSourceItemApi("https://coupleoutfit.example", async () => json({ code: "SOURCE_STORAGE_UNAVAILABLE" }, 503));
  await assert.rejects(failingApi.completeUpload("source-1", "asset-1"), (error: unknown) => error instanceof ApiError && error.code === "SOURCE_STORAGE_UNAVAILABLE");
});

test("runs image creation, upload and synchronous completion to the stable source boundary", async () => {
  const phases: string[] = [];
  const calls: string[] = [];
  const api: SourceItemApi = {
    async createFromImage() { calls.push("create"); return source(); },
    async uploadFile(value) { calls.push(`upload:${value.assetId}`); },
    async completeUpload(id, assetId) { calls.push(`complete:${id}:${assetId}`); return source({ status: "READY_FOR_ANALYSIS", upload: null, asset: { assetId, status: "VERIFIED", mediaType: "image/png", width: 2, height: 3 } }); },
    async createFromUrl() { throw new Error("unused"); },
    async authorizeUpload() { throw new Error("unused"); },
    async get() { throw new Error("unused"); },
  };
  const result = await uploadImageSource(api, new Blob(["image"], { type: "image/png" }), { onPhase: (phase) => phases.push(phase) });
  assert.deepEqual(calls, ["create", "upload:asset-1", "complete:source-1:asset-1"]);
  assert.deepEqual(phases, ["authorizing", "uploading", "verifying"]);
  assert.equal(result.status, "READY_FOR_ANALYSIS");
});

test("uses upload authorization on the existing unsupported URL source", async () => {
  const calls: string[] = [];
  const api: SourceItemApi = {
    async authorizeUpload(id) { calls.push(`authorize:${id}`); return source({ sourceItemId: id, sourceType: "PRODUCT_URL" }); },
    async uploadFile() { calls.push("upload"); },
    async completeUpload() { calls.push("complete"); return source({ sourceType: "PRODUCT_URL", status: "READY_FOR_ANALYSIS", upload: null }); },
    async createFromImage() { throw new Error("must preserve source identity"); },
    async createFromUrl() { throw new Error("unused"); },
    async get() { throw new Error("unused"); },
  };
  await uploadImageSource(api, new Blob(["image"]), { fallbackSourceId: "url-source" });
  assert.deepEqual(calls, ["authorize:url-source", "upload", "complete"]);
});

test("retains a safe resume point after upload and completion failures", async () => {
  let failCompletion = true;
  const api: SourceItemApi = {
    async createFromImage() { return source(); },
    async uploadFile() {},
    async completeUpload() {
      if (failCompletion) throw new ApiError(503, "trace", "SOURCE_STORAGE_UNAVAILABLE");
      return source({ status: "READY_FOR_ANALYSIS", upload: null });
    },
    async createFromUrl() { throw new Error("unused"); },
    async authorizeUpload() { throw new Error("must not re-upload verified bytes"); },
    async get() { throw new Error("unused"); },
  };
  let resume = null;
  await assert.rejects(uploadImageSource(api, new Blob(["image"])), (error: unknown) => {
    assert.ok(error instanceof ImageWorkflowError);
    resume = error.resume;
    assert.deepEqual(error.resume, { sourceItemId: "source-1", assetId: "asset-1", uploaded: true });
    return true;
  });
  failCompletion = false;
  assert.equal((await uploadImageSource(api, new Blob(["image"]), { resume })).status, "READY_FOR_ANALYSIS");
});

test("prevents concurrent duplicate submissions and allows a later retry", () => {
  const gate = new SubmissionGate();
  assert.equal(gate.tryEnter(), true);
  assert.equal(gate.tryEnter(), false);
  gate.leave();
  assert.equal(gate.tryEnter(), true);
});

test("maps terminal, temporary and ownership errors to distinct customer language", () => {
  assert.match(sourceErrorMessage(new ApiError(415, undefined, "SOURCE_IMAGE_TYPE_UNSUPPORTED")), /format/);
  assert.match(sourceErrorMessage(new ApiError(503, undefined, "SOURCE_STORAGE_UNAVAILABLE")), /temporarily/);
  assert.match(sourceErrorMessage(new ApiError(404, undefined, "SOURCE_NOT_FOUND")), /session/);
});
