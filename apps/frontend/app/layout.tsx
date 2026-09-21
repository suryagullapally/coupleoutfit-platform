import type { Metadata } from "next";
import type { ReactNode } from "react";
import { environment } from "@/config/environment";
import { defaultMarket } from "@/config/market";
import { MarketProvider } from "@/components/market-provider";
import "@/styles/globals.css";

export const metadata: Metadata = {
  metadataBase: new URL(environment.siteUrl),
  applicationName: "CoupleOutfit",
  title: { default: "CoupleOutfit", template: "%s | CoupleOutfit" },
  description: "Discover matching partner outfits from real products you can buy.",
  robots: { index: true, follow: true },
};

export default function RootLayout({ children }: { children: ReactNode }) {
  return (
    <html lang={defaultMarket.localeCode}>
      <body className="font-sans antialiased">
        <a href="#main-content" className="sr-only focus:not-sr-only focus:fixed focus:top-4 focus:left-4 focus:z-50 focus:rounded-full focus:bg-ink focus:px-5 focus:py-3 focus:text-canvas">
          Skip to content
        </a>
        <MarketProvider market={defaultMarket}>{children}</MarketProvider>
      </body>
    </html>
  );
}
