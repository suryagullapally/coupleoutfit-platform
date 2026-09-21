import { ApiError, NetworkError } from "../../lib/api/errors.ts";
import type { SourceItemApi } from "./api.ts";
import type { SourceItemResponse, SourceUploadAuthorization } from "./types.ts";

export type ImagePhase = "authorizing" | "uploading" | "verifying";

export class SubmissionGate {
  private active = false;
  tryEnter(): boolean {
    if (this.active) return false;
    this.active = true;
    return true;
  }
  leave(): void { this.active = false; }
}

export interface ImageResumePoint {
  readonly sourceItemId: string;
  readonly assetId: string;
  readonly uploaded: boolean;
}

export class ImageWorkflowError extends Error {
  readonly reason: unknown;
  readonly resume: ImageResumePoint | null;
  constructor(reason: unknown, resume: ImageResumePoint | null) {
    super("The image workflow did not complete.");
    this.name = "ImageWorkflowError";
    this.reason = reason;
    this.resume = resume;
  }
}

export async function uploadImageSource(
  api: SourceItemApi,
  file: Blob,
  options: {
    fallbackSourceId?: string;
    resume?: ImageResumePoint | null;
    onPhase?: (phase: ImagePhase) => void;
  } = {},
): Promise<SourceItemResponse> {
  let resume = options.resume ?? null;
  let authorization: SourceUploadAuthorization | null = null;
  try {
    if (!resume) {
      options.onPhase?.("authorizing");
      const source = options.fallbackSourceId
        ? await api.authorizeUpload(options.fallbackSourceId)
        : await api.createFromImage();
      if (!source.asset || !source.upload || source.status !== "AWAITING_UPLOAD" || source.asset.assetId !== source.upload.assetId) {
        throw new Error("Invalid upload authorization contract.");
      }
      authorization = source.upload;
      resume = { sourceItemId: source.sourceItemId, assetId: source.asset.assetId, uploaded: false };
    } else if (!resume.uploaded) {
      options.onPhase?.("authorizing");
      const renewed = await api.authorizeUpload(resume.sourceItemId);
      if (!renewed.asset || !renewed.upload || renewed.asset.assetId !== resume.assetId) throw new Error("Invalid renewed upload authorization contract.");
      authorization = renewed.upload;
    }

    if (!resume.uploaded) {
      if (!authorization) throw new Error("Upload authorization is missing.");
      options.onPhase?.("uploading");
      await api.uploadFile(authorization, file);
      resume = { ...resume, uploaded: true };
    }

    options.onPhase?.("verifying");
    return await api.completeUpload(resume.sourceItemId, resume.assetId);
  } catch (error) {
    throw new ImageWorkflowError(error, resume);
  }
}

export function sourceErrorMessage(error: unknown): string {
  const reason = error instanceof ImageWorkflowError ? error.reason : error;
  if (reason instanceof NetworkError) return "We couldn't reach CoupleOutfit. Check your connection and try again.";
  if (reason instanceof ApiError) {
    switch (reason.code) {
      case "SOURCE_URL_INVALID": return "That product link cannot be used. Check it and try again.";
      case "SOURCE_IMAGE_TYPE_UNSUPPORTED": return "That image format cannot be used. Choose a JPG, PNG or WebP image.";
      case "SOURCE_IMAGE_TOO_LARGE": return "That image is too large. Choose one smaller than 10 MB.";
      case "SOURCE_IMAGE_INVALID": return "We couldn't read that image. Choose another file.";
      case "SOURCE_UPLOAD_EXPIRED": return "The upload permission expired. Try the upload again.";
      case "SOURCE_UPLOAD_NOT_FOUND": return "The image upload could not be found. Choose the image and try again.";
      case "SOURCE_STORAGE_UNAVAILABLE":
      case "SOURCE_VERIFICATION_BUSY": return "Image uploads are temporarily busy. Please try again.";
      case "SOURCE_NOT_FOUND":
      case "SOURCE_REQUEST_FORBIDDEN": return "This source session is no longer available. Start again with the link or image.";
      case "SOURCE_INVALID_STATE": return "The source changed while it was being processed. Check its status and try again.";
    }
    if (reason.status >= 500) return "CoupleOutfit is temporarily unavailable. Please try again.";
  }
  return "We couldn't prepare that outfit. Please try again.";
}

export function sourceErrorCode(error: unknown): string | undefined {
  const reason = error instanceof ImageWorkflowError ? error.reason : error;
  return reason instanceof ApiError ? reason.code : undefined;
}
