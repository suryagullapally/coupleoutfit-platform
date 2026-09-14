"use client";

import { createContext, useContext, type ReactNode } from "react";
import type { Market } from "@/types/market";

const MarketContext = createContext<Market | null>(null);

export function MarketProvider({ market, children }: { market: Market; children: ReactNode }) {
  return <MarketContext.Provider value={market}>{children}</MarketContext.Provider>;
}

export function useMarket(): Market {
  const market = useContext(MarketContext);
  if (!market) throw new Error("useMarket requires a MarketProvider.");
  return market;
}
