export const SOURCE_IMAGE_MAX_BYTES = 10_485_760;
export const SOURCE_IMAGE_TYPES = Object.freeze(["image/jpeg", "image/png", "image/webp"] as const);

export function validateProductUrl(value: string): string | null {
  const trimmed = value.trim();
  if (!trimmed) return "Enter a product link to continue.";
  try {
    const url = new URL(trimmed);
    if (!['http:', 'https:'].includes(url.protocol) || !url.hostname || url.username || url.password) throw new Error();
  } catch {
    return "Enter a complete product link beginning with http:// or https://.";
  }
  return null;
}

export function validateSourceImage(file: Pick<File, "size" | "type">): string | null {
  if (!SOURCE_IMAGE_TYPES.includes(file.type as (typeof SOURCE_IMAGE_TYPES)[number])) {
    return "Choose a JPG, PNG or WebP image.";
  }
  if (file.size <= 0) return "Choose an image that is not empty.";
  if (file.size > SOURCE_IMAGE_MAX_BYTES) return "Choose an image smaller than 10 MB.";
  return null;
}
