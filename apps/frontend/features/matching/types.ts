export type ProductTarget = "MENSWEAR" | "WOMENSWEAR";
export type MatchMode = "EXACT" | "COORDINATED" | "COMPLEMENTARY";
export type MatchStatus = "CREATED" | "MATCHING" | "COMPLETED" | "FAILED";
export type ScoreBand = "EXCELLENT" | "GREAT" | "GOOD" | "POSSIBLE" | "BELOW_THRESHOLD";
export type AvailabilityStatus = "IN_STOCK" | "OUT_OF_STOCK" | "UNKNOWN";

export const productTargets: readonly ProductTarget[] = ["MENSWEAR", "WOMENSWEAR"];
export const matchModes: readonly MatchMode[] = ["EXACT", "COORDINATED", "COMPLEMENTARY"];
export const matchStatuses: readonly MatchStatus[] = ["CREATED", "MATCHING", "COMPLETED", "FAILED"];
export const DEFAULT_MATCH_MODE: MatchMode = "COORDINATED";

export interface CreateMatchSessionRequest {
  readonly sourceItemId: string;
  readonly analysisRevisionId: string;
  readonly partnerTarget: ProductTarget;
  readonly matchMode: MatchMode;
  readonly sameRetailerOnly: boolean;
}

export interface MatchSessionResponse {
  readonly id: string;
  readonly sourceItemId: string;
  readonly analysisRevisionId: string;
  readonly status: MatchStatus;
  readonly market: {
    readonly countryCode: string;
    readonly currencyCode: string;
    readonly localeCode: string;
  };
  readonly configuration: {
    readonly partnerTarget: ProductTarget;
    readonly matchMode: MatchMode;
    readonly sameRetailerOnly: boolean;
  };
  readonly algorithmVersion: string;
  readonly failureCode: string | null;
  readonly results: readonly MatchResult[];
}

export interface MatchResult {
  readonly resultId: string;
  readonly rank: number;
  readonly coupleMatchScore: number;
  readonly scoreBand: ScoreBand;
  readonly explanation: { readonly reasons: readonly string[] };
  readonly product: {
    readonly id: string;
    readonly name: string;
    readonly brand: string | null;
    readonly category: string;
  };
  readonly retailer: {
    readonly id: string;
    readonly slug: string;
    readonly name: string;
  };
  readonly offer: {
    readonly offerId: string;
    readonly amountMinor: number | null;
    readonly currencyCode: string | null;
    readonly availability: AvailabilityStatus;
    readonly imageUrl: string | null;
  };
  readonly purchase: {
    readonly available: boolean;
    readonly url: string | null;
  };
}
