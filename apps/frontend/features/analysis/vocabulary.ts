import type { Fit, Formality, Pattern, ProductCategory } from "./types.ts";

export const categories: readonly ProductCategory[] = ["TOP", "SHIRT", "T_SHIRT", "KNITWEAR", "DRESS", "JUMPSUIT", "TROUSERS", "JEANS", "SKIRT", "SHORTS", "SUIT", "JACKET", "COAT", "FOOTWEAR", "ACCESSORY", "OTHER"];
export const subcategories = ["MIDI_DRESS", "MAXI_DRESS", "MINI_DRESS", "SHIRT", "T_SHIRT", "BLOUSE", "TROUSERS", "JEANS", "SUIT", "JACKET", "COAT", "KNITWEAR", "FOOTWEAR", "SKIRT", "SHORTS", "JUMPSUIT", "OTHER"] as const;
export const colors = ["BLACK", "WHITE", "GREY", "NAVY", "BLUE", "LIGHT_BLUE", "RED", "BURGUNDY", "PINK", "PURPLE", "GREEN", "OLIVE", "YELLOW", "ORANGE", "BROWN", "BEIGE", "CREAM", "GOLD", "SILVER", "MULTICOLOUR", "UNKNOWN"] as const;
export const patterns: readonly Pattern[] = ["SOLID", "STRIPED", "CHECKED", "FLORAL", "POLKA_DOT", "ANIMAL", "PRINTED", "TEXTURED", "COLOUR_BLOCK", "UNKNOWN"];
export const materials = ["DENIM", "SATIN_LIKE", "LEATHER_LIKE", "KNIT", "COTTON_LIKE", "LINEN_LIKE", "WOOL_LIKE", "SHEER", "VELVET_LIKE", "SEQUINED", "SYNTHETIC_LIKE", "UNKNOWN"] as const;
export const fits: readonly Fit[] = ["SLIM", "REGULAR", "RELAXED", "OVERSIZED", "FITTED", "STRUCTURED", "FLOWING", "UNKNOWN"];
export const formalities: readonly Formality[] = ["CASUAL", "SMART_CASUAL", "SMART", "FORMAL", "EVENING", "SPORTY", "UNKNOWN"];
export const seasons = ["SPRING", "SUMMER", "AUTUMN", "WINTER"] as const;
export const styles = ["CLASSIC", "MINIMAL", "ELEGANT", "EVENING", "SPORTY", "STREETWEAR", "BOHEMIAN", "ROMANTIC", "EDGY", "PREPPY", "TAILORED", "RELAXED", "VINTAGE", "CONTEMPORARY", "UTILITY", "FESTIVE", "UNKNOWN"] as const;
export const details = ["MIDI", "MAXI", "MINI", "SLEEVELESS", "SHORT_SLEEVE", "LONG_SLEEVE", "COLLARED", "V_NECK", "CREW_NECK", "HIGH_NECK", "BUTTONED", "ZIPPER", "PLEATED", "RUFFLED", "LACE", "EMBROIDERED", "BELTED", "LAYERED", "MULTI_GARMENT", "POCKETS", "HOODED", "CROPPED", "WIDE_LEG", "STRAIGHT_LEG", "TAPERED", "UNKNOWN"] as const;

const labelOverrides: Readonly<Record<string, string>> = {
  T_SHIRT: "T-shirt",
  POLKA_DOT: "Polka dot",
  COLOUR_BLOCK: "Colour block",
  SATIN_LIKE: "Satin-like",
  LEATHER_LIKE: "Leather-like",
  COTTON_LIKE: "Cotton-like",
  LINEN_LIKE: "Linen-like",
  WOOL_LIKE: "Wool-like",
  VELVET_LIKE: "Velvet-like",
  SYNTHETIC_LIKE: "Synthetic-like",
  V_NECK: "V-neck",
  MULTI_GARMENT: "Multi-garment",
  TAPERED: "Tapered",
};

export function tokenLabel(value: string): string {
  return labelOverrides[value] ?? value.toLowerCase().replaceAll("_", " ").replace(/^./, (letter) => letter.toUpperCase());
}
