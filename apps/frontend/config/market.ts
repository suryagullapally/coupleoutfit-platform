import type { Market } from "../types/market.ts";

// ADR-001: launch defaults belong here, never in shared presentation components.
export const defaultMarket: Market = Object.freeze({
  countryCode: "GB",
  currencyCode: "GBP",
  localeCode: "en-GB",
});
