import { test } from "node:test";
import assert from "node:assert/strict";
import { createApiClient } from "../lib/api/client.ts";
import { ApiError, ApiResponseError, NetworkError } from "../lib/api/errors.ts";

const json = (body: unknown, status = 200, headers = {}) => new Response(JSON.stringify(body), { status, headers: { "Content-Type": "application/json", ...headers } });

test("joins base paths, sends JSON/custom headers and captures request IDs", async () => {
  const client = createApiClient("https://service.example/base", async (url, init) => {
    assert.equal(String(url), "https://service.example/base/probe");
    assert.equal(init?.body, '{"value":1}');
    assert.equal(new Headers(init?.headers).get("X-Custom"), "yes");
    assert.equal(new Headers(init?.headers).get("Content-Type"), "application/json");
    assert.equal(init?.credentials, "omit");
    return json({ value: 2 }, 200, { "X-Request-Id": "trace-1" });
  });
  assert.deepEqual(await client.request<{ value: number }>("/probe", { method: "POST", json: { value: 1 }, headers: { "X-Custom": "yes" } }), { data: { value: 2 }, status: 200, requestId: "trace-1" });
});

test("handles empty responses", async () => {
  const client = createApiClient("https://service.example", async () => new Response(null, { status: 204 }));
  assert.equal((await client.request("/probe")).data, undefined);
});

test("retains HTTP status and safe problem correlation without server details", async () => {
  const client = createApiClient("https://service.example", async () => json({ code: "VALIDATION_FAILED", traceId: "trace-2", detail: "secret stack trace" }, 400, { "Content-Type": "application/problem+json" }));
  await assert.rejects(client.request("/probe"), (error: unknown) => {
    assert.ok(error instanceof ApiError);
    assert.equal(error.status, 400);
    assert.equal(error.code, "VALIDATION_FAILED");
    assert.equal(error.requestId, "trace-2");
    assert.ok(!JSON.stringify(error).includes("secret"));
    assert.ok(!error.message.includes("secret"));
    return true;
  });
});

test("handles HTML errors and invalid successful payloads predictably", async () => {
  const failed = createApiClient("https://service.example", async () => new Response("secret internal error", { status: 502 }));
  await assert.rejects(failed.request("/probe"), ApiError);
  for (const response of [new Response("text"), new Response("{", { headers: { "Content-Type": "application/json" } })]) {
    const client = createApiClient("https://service.example", async () => response);
    await assert.rejects(client.request("/probe"), ApiResponseError);
  }
});

test("separates network failures from caller cancellation", async () => {
  const failed = createApiClient("https://service.example", async () => { throw new TypeError("private connection information"); });
  await assert.rejects(failed.request("/probe"), NetworkError);
  const controller = new AbortController();
  controller.abort();
  const cancelled = createApiClient("https://service.example", async () => { throw controller.signal.reason; });
  await assert.rejects(cancelled.request("/probe", { signal: controller.signal }), { name: "AbortError" });
});

test("rejects destinations outside the configured API base", async () => {
  const client = createApiClient("https://service.example/base", async () => { assert.fail("must not fetch"); });
  for (const path of ["https://other.example", "//other.example", "../escape", "%2e%2e/escape", "\\other.example"]) {
    await assert.rejects(client.request(path), TypeError);
  }
  assert.throws(() => createApiClient("https://user:secret@service.example"), TypeError);
});
