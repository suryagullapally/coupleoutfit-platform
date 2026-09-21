export type AnalysisStatus = "PENDING" | "IN_PROGRESS" | "COMPLETED" | "FAILED";
export type RevisionType = "AI_GENERATED" | "USER_CORRECTED";

export type ProductCategory = "TOP" | "SHIRT" | "T_SHIRT" | "KNITWEAR" | "DRESS" | "JUMPSUIT" | "TROUSERS" | "JEANS" | "SKIRT" | "SHORTS" | "SUIT" | "JACKET" | "COAT" | "FOOTWEAR" | "ACCESSORY" | "OTHER";
export type Pattern = "SOLID" | "STRIPED" | "CHECKED" | "FLORAL" | "POLKA_DOT" | "ANIMAL" | "PRINTED" | "TEXTURED" | "COLOUR_BLOCK" | "UNKNOWN";
export type Fit = "SLIM" | "REGULAR" | "RELAXED" | "OVERSIZED" | "FITTED" | "STRUCTURED" | "FLOWING" | "UNKNOWN";
export type Formality = "CASUAL" | "SMART_CASUAL" | "SMART" | "FORMAL" | "EVENING" | "SPORTY" | "UNKNOWN";

export interface ClothingAttributes {
  readonly category: ProductCategory;
  readonly subcategory: string | null;
  readonly primaryColors: readonly string[];
  readonly secondaryColors: readonly string[];
  readonly pattern: Pattern;
  readonly materialAppearance: readonly string[];
  readonly fit: Fit;
  readonly formality: Formality;
  readonly seasonality: readonly string[];
  readonly styleTags: readonly string[];
  readonly details: readonly string[];
}

export interface AttributeConfidence {
  readonly category: number | null;
  readonly primaryColors: number | null;
  readonly materialAppearance: number | null;
}

export interface AnalysisRevision {
  readonly id: string;
  readonly revisionNumber: number;
  readonly revisionType: RevisionType;
  readonly schemaVersion: string;
  readonly attributes: ClothingAttributes;
  readonly confidence: AttributeConfidence | null;
}

export interface ClothingAnalysisResponse {
  readonly id: string;
  readonly status: AnalysisStatus;
  readonly currentRevision: AnalysisRevision | null;
  readonly failureCode: string | null;
}
