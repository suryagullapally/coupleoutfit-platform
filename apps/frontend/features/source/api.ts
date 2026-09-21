import { createApiClient } from "../../lib/api/client.ts";
import { ApiError, ApiResponseError, NetworkError } from "../../lib/api/errors.ts";
import type {
  AssetStatus,
  SourceItemResponse,
  SourceStatus,
  SourceType,
  SourceUploadAuthorization,
  UrlResolutionStatus,
} from "./types.ts";

const sourceTypes: readonly SourceType[] = ["IMAGE_UPLOAD", "PRODUCT_URL"];
const sourceStatuses: readonly SourceStatus[] = ["AWAITING_UPLOAD", "UPLOAD_VERIFYING", "RESOLVING_URL", "READY_FOR_ANALYSIS", "IMAGE_UPLOAD_REQUIRED", "FAILED"];
const assetStatuses: readonly AssetStatus[] = ["PENDING", "VERIFIED", "REJECTED", "DELETED"];
const resolutionStatuses: readonly UrlResolutionStatus[] = ["RESOLVED_CATALOGUE_OFFER", "UNSUPPORTED"];

function record(value: unknown): Record<string, unknown> {
  if (!value || typeof value !== "object" || Array.isArray(value)) throw new ApiResponseError();
  return value as Record<string, unknown>;
}

function string(value: unknown): string {
  if (typeof value !== "string" || !value) throw new ApiResponseError();
  return value;
}

function nullableString(value: unknown): string | null {
  if (value === null) return null;
  return string(value);
}

function member<T extends string>(value: unknown, values: readonly T[]): T {
  if (typeof value !== "string" || !values.includes(value as T)) throw new ApiResponseError();
  return value as T;
}

export function parseSourceItemResponse(value: unknown): SourceItemResponse {
  const data = record(value);
  const market = record(data.market);
  const asset = data.asset === null ? null : record(data.asset);
  const resolution = data.urlResolution === null ? null : record(data.urlResolution);
  const upload = data.upload === null ? null : record(data.upload);
  return {
    sourceItemId: string(data.sourceItemId),
    sourceType: member(data.sourceType, sourceTypes),
    status: member(data.status, sourceStatuses),
    market: {
      countryCode: string(market.countryCode),
      currencyCode: string(market.currencyCode),
      localeCode: string(market.localeCode),
    },
    asset: asset && {
      assetId: string(asset.assetId),
      status: member(asset.status, assetStatuses),
      mediaType: nullableString(asset.mediaType),
      width: asset.width === null ? null : number(asset.width),
      height: asset.height === null ? null : number(asset.height),
    },
    urlResolution: resolution && {
      status: member(resolution.status, resolutionStatuses),
      retailerMarketId: nullableString(resolution.retailerMarketId),
      productOfferId: nullableString(resolution.productOfferId),
      reason: nullableString(resolution.reason),
    },
    upload: upload && {
      assetId: string(upload.assetId),
      uploadUrl: string(upload.uploadUrl),
      expiresAt: string(upload.expiresAt),
      method: string(upload.method),
    },
    action: nullableString(data.action),
  };
}

function number(value: unknown): number {
  if (typeof value !== "number" || !Number.isFinite(value)) throw new ApiResponseError();
  return value;
}

function safeToken(value: unknown): string | undefined {
  return typeof value === "string" && /^[A-Za-z0-9][A-Za-z0-9._-]{0,63}$/.test(value) ? value : undefined;
}

async function uploadProblem(response: Response): Promise<ApiError> {
  let code: string | undefined;
  if (/application\/(?:problem\+)?json/i.test(response.headers.get("Content-Type") ?? "")) {
    try { code = safeToken(record(await response.json()).code); } catch { /* Signed-storage responses are not trusted as JSON contracts. */ }
  }
  return new ApiError(response.status, safeToken(response.headers.get("X-Request-Id")), code);
}

export interface SourceItemApi {
  createFromUrl(url: string): Promise<SourceItemResponse>;
  createFromImage(): Promise<SourceItemResponse>;
  authorizeUpload(sourceItemId: string): Promise<SourceItemResponse>;
  uploadFile(authorization: SourceUploadAuthorization, file: Blob): Promise<void>;
  completeUpload(sourceItemId: string, assetId: string): Promise<SourceItemResponse>;
  get(sourceItemId: string, signal?: AbortSignal): Promise<SourceItemResponse>;
}

export function createSourceItemApi(origin: string, fetcher: typeof fetch = fetch): SourceItemApi {
  const site = new URL(origin);
  if (!['http:', 'https:'].includes(site.protocol) || site.username || site.password || site.pathname !== "/" || site.search || site.hash) {
    throw new TypeError("Source API origin must be an HTTP(S) origin.");
  }
  const client = createApiClient(site.href, fetcher, "same-origin");
  const writeHeaders = { "X-Source-Request": "1" };
  const request = async (path: string, options?: Parameters<typeof client.request>[1]) => {
    const response = await client.request<unknown>(path, options);
    return parseSourceItemResponse(response.data);
  };
  return {
    createFromUrl: (url) => request("/api/v1/source-items", { method: "POST", headers: writeHeaders, json: { sourceType: "PRODUCT_URL", url } }),
    createFromImage: () => request("/api/v1/source-items", { method: "POST", headers: writeHeaders, json: { sourceType: "IMAGE_UPLOAD" } }),
    authorizeUpload: (id) => request(`/api/v1/source-items/${encodeURIComponent(id)}/upload-authorization`, { method: "POST", headers: writeHeaders }),
    completeUpload: (id, assetId) => request(`/api/v1/source-items/${encodeURIComponent(id)}/upload-complete`, { method: "POST", headers: writeHeaders, json: { assetId } }),
    get: (id, signal) => request(`/api/v1/source-items/${encodeURIComponent(id)}`, { signal }),
    async uploadFile(authorization, file) {
      if (authorization.method !== "PUT") throw new ApiResponseError();
      const destination = new URL(authorization.uploadUrl, site);
      if (!['http:', 'https:'].includes(destination.protocol) || destination.username || destination.password || destination.hash) throw new ApiResponseError();
      let response: Response;
      try {
        response = await fetcher(destination, {
          method: "PUT",
          headers: { "Content-Type": file.type || "application/octet-stream" },
          body: file,
          credentials: destination.origin === site.origin ? "same-origin" : "omit",
        });
      } catch (error) {
        if (error instanceof Error && error.name === "AbortError") throw error;
        throw new NetworkError();
      }
      if (!response.ok) throw await uploadProblem(response);
    },
  };
}
