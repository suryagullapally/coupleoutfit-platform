function publicUrl(value: string, name: string): string {
  const url = new URL(value);
  if (!['http:', 'https:'].includes(url.protocol) || url.username || url.password || url.search || url.hash) {
    throw new Error(`${name} must be an HTTP(S) URL without credentials, query, or fragment.`);
  }
  return url.href.replace(/\/$/, "");
}

// Direct references let Next.js inline only these deliberately public values.
export const environment = Object.freeze({
  apiBaseUrl: publicUrl(process.env.NEXT_PUBLIC_API_BASE_URL ?? "http://localhost:8080", "NEXT_PUBLIC_API_BASE_URL"),
  siteUrl: publicUrl(process.env.NEXT_PUBLIC_SITE_URL ?? "http://localhost:3000", "NEXT_PUBLIC_SITE_URL"),
});
