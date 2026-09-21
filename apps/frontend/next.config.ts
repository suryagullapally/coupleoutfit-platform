import type { NextConfig } from "next";

function backendBaseUrl(): string {
  const value = process.env.NEXT_PUBLIC_API_BASE_URL ?? "http://localhost:8080";
  const url = new URL(value);
  if (!['http:', 'https:'].includes(url.protocol) || url.username || url.password || url.search || url.hash) {
    throw new Error("NEXT_PUBLIC_API_BASE_URL must be an HTTP(S) URL without credentials, query, or fragment.");
  }
  return url.href.replace(/\/$/, "");
}

const backend = backendBaseUrl();

const nextConfig: NextConfig = {
  async rewrites() {
    return [
      { source: "/api/:path*", destination: `${backend}/api/:path*` },
      { source: "/internal/:path*", destination: `${backend}/internal/:path*` },
      { source: "/go/:token", destination: `${backend}/go/:token` },
    ];
  },
};

export default nextConfig;
