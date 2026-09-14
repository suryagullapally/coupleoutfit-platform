import type { ApiRequestOptions, ApiResponse } from "../../types/api.ts";
import { ApiError, ApiResponseError, NetworkError } from "./errors.ts";

function safeToken(value: unknown): string | undefined {
  return typeof value === "string" && /^[A-Za-z0-9][A-Za-z0-9._-]{0,63}$/.test(value) ? value : undefined;
}

/** JSON-only transport. T describes a caller's contract; it does not validate runtime payloads. */
export function createApiClient(baseUrl: string, fetcher: typeof fetch = fetch) {
  const base = new URL(baseUrl.endsWith("/") ? baseUrl : `${baseUrl}/`);
  if (!["http:", "https:"].includes(base.protocol) || base.username || base.password || base.search || base.hash) {
    throw new TypeError("API base must be an HTTP(S) URL without credentials, query, or fragment.");
  }

  return {
    async request<T>(path: string, options: ApiRequestOptions = {}): Promise<ApiResponse<T>> {
      // Keep calls under the configured API base; never accept an arbitrary destination URL.
      if (/^(?:[a-z][a-z0-9+.-]*:|\/\/)/i.test(path) || path.includes("\\")) {
        throw new TypeError("API path must be relative to the configured base.");
      }
      const url = new URL(path.replace(/^\//, ""), base);
      if (url.origin !== base.origin || !url.pathname.startsWith(base.pathname)) {
        throw new TypeError("API path must remain under the configured base.");
      }
      const { json, headers: suppliedHeaders, ...init } = options;
      const headers = new Headers(suppliedHeaders);
      if (!headers.has("Accept")) headers.set("Accept", "application/json");
      if (json !== undefined) headers.set("Content-Type", "application/json");
      const body = json === undefined ? undefined : JSON.stringify(json);
      let response: Response;
      try {
        response = await fetcher(url, { ...init, headers, body, credentials: "omit" });
      } catch (error) {
        if (init.signal?.aborted || (error instanceof Error && error.name === "AbortError")) throw error;
        throw new NetworkError();
      }
      let requestId = safeToken(response.headers.get("X-Request-Id"));
      if (response.status === 204 || response.status === 205 || init.method?.toUpperCase() === "HEAD") {
        if (!response.ok) throw new ApiError(response.status, requestId);
        return { data: undefined, status: response.status, requestId };
      }
      const isJson = /^(?:application\/json|application\/[a-z0-9.+-]+\+json)(?:;|$)/i.test(response.headers.get("Content-Type") ?? "");
      let data: unknown;
      if (isJson) {
        try { data = await response.json(); }
        catch {
          if (init.signal?.aborted) throw init.signal.reason;
          if (response.ok) throw new ApiResponseError(requestId);
        }
      }
      if (!response.ok) {
        const problem = data && typeof data === "object" ? data as Record<string, unknown> : {};
        requestId ??= safeToken(problem.traceId);
        // Never copy arbitrary server messages, HTML, stack traces or bodies into UI errors.
        throw new ApiError(response.status, requestId, safeToken(problem.code));
      }
      if (!isJson) throw new ApiResponseError(requestId);
      return { data: data as T, status: response.status, requestId };
    },
  };
}
