import type { Metadata } from "next";
import type { ReactNode } from "react";
import { environment } from "@/config/environment";
import { defaultMarket } from "@/config/market";
import { MarketProvider } from "@/components/market-provider";
import "@/styles/globals.css";

export const metadata: Metadata = {
  metadataBase: new URL(environment.siteUrl),
  applicationName: "CoupleOutfit",
  title: { default: "CoupleOutfit — Frontend foundation", template: "%s | CoupleOutfit" },
  description: "The CoupleOutfit application foundation. More to come.",
  robots: { index: true, follow: true },
};

export default function RootLayout({ children }: { children: ReactNode }) {
  return (
    <html lang={defaultMarket.localeCode}>
      <body className="font-sans antialiased">
        <a href="#main-content" className="sr-only focus:not-sr-only focus:fixed focus:top-4 focus:left-4 focus:z-50 focus:rounded-lg focus:bg-surface focus:px-5 focus:py-3">
          Skip to content
        </a>
        <MarketProvider market={defaultMarket}>{children}</MarketProvider>
      </body>
    </html>
  );
}
