import { formatMoney } from "../../lib/money.ts";
import type { AvailabilityStatus, MatchMode, ProductTarget, ScoreBand } from "./types.ts";

const scoreLabels: Readonly<Record<ScoreBand, string>> = {
  EXCELLENT: "Excellent match",
  GREAT: "Great match",
  GOOD: "Good match",
  POSSIBLE: "Possible match",
  BELOW_THRESHOLD: "Below threshold",
};

export function scoreBandLabel(band: ScoreBand): string {
  return scoreLabels[band];
}

export function targetLabel(target: ProductTarget): string {
  return target === "MENSWEAR" ? "men's outfit" : "women's outfit";
}

export function matchModeLabel(mode: MatchMode): string {
  return mode === "EXACT" ? "Exact" : mode === "COORDINATED" ? "Coordinated" : "Complementary";
}

export function availabilityLabel(status: AvailabilityStatus): string {
  if (status === "IN_STOCK") return "In stock";
  if (status === "OUT_OF_STOCK") return "Out of stock";
  return "Check availability";
}

export function offerPrice(amountMinor: number | null, currencyCode: string | null, localeCode: string): string | null {
  if (amountMinor === null || currencyCode === null) return null;
  return formatMoney(amountMinor, currencyCode, localeCode);
}
