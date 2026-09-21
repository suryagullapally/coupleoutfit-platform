import type { Market } from "@/types/market";

export type SourceType = "IMAGE_UPLOAD" | "PRODUCT_URL";
export type SourceStatus = "AWAITING_UPLOAD" | "UPLOAD_VERIFYING" | "RESOLVING_URL" | "READY_FOR_ANALYSIS" | "IMAGE_UPLOAD_REQUIRED" | "FAILED";
export type AssetStatus = "PENDING" | "VERIFIED" | "REJECTED" | "DELETED";
export type UrlResolutionStatus = "RESOLVED_CATALOGUE_OFFER" | "UNSUPPORTED";

export interface SourceAsset {
  readonly assetId: string;
  readonly status: AssetStatus;
  readonly mediaType: string | null;
  readonly width: number | null;
  readonly height: number | null;
}

export interface SourceUrlResolution {
  readonly status: UrlResolutionStatus;
  readonly retailerMarketId: string | null;
  readonly productOfferId: string | null;
  readonly reason: string | null;
}

export interface SourceUploadAuthorization {
  readonly assetId: string;
  readonly uploadUrl: string;
  readonly expiresAt: string;
  readonly method: string;
}

export interface SourceItemResponse {
  readonly sourceItemId: string;
  readonly sourceType: SourceType;
  readonly status: SourceStatus;
  readonly market: Market;
  readonly asset: SourceAsset | null;
  readonly urlResolution: SourceUrlResolution | null;
  readonly upload: SourceUploadAuthorization | null;
  readonly action: string | null;
}
