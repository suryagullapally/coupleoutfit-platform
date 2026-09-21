"use client";

import { useEffect, useId, useRef, useState, type FormEvent } from "react";
import Image from "next/image";
import { useRouter } from "next/navigation";
import { homepageContent } from "@/features/home/content";
import { createSourceItemApi } from "@/features/source/api";
import { validateProductUrl, validateSourceImage } from "@/features/source/validation";
import { ImageWorkflowError, sourceErrorCode, sourceErrorMessage, SubmissionGate, uploadImageSource, type ImagePhase, type ImageResumePoint } from "@/features/source/workflow";
import type { SourceItemResponse, SourceStatus } from "@/features/source/types";

type BusyState = "url" | ImagePhase | null;

const statusMessages: Record<SourceStatus, string> = {
  AWAITING_UPLOAD: "Your image is ready to upload.",
  UPLOAD_VERIFYING: "Checking your image…",
  RESOLVING_URL: "Checking that product page…",
  READY_FOR_ANALYSIS: "Your outfit is saved and ready.",
  IMAGE_UPLOAD_REQUIRED: "We couldn't use that product page. Upload a photo or screenshot of the outfit instead.",
  FAILED: "We couldn't prepare that outfit. Choose another source and try again.",
};

const busyMessages: Record<Exclude<BusyState, null>, string> = {
  url: "Checking product page…",
  authorizing: "Preparing a secure upload…",
  uploading: "Uploading your image…",
  verifying: "Checking your image…",
};

export function SourceEntry() {
  const router = useRouter();
  const inputId = useId();
  const helpId = useId();
  const errorId = useId();
  const statusId = useId();
  const uploadId = useId();
  const uploadErrorId = useId();
  const fileInput = useRef<HTMLInputElement>(null);
  const previewObjectUrl = useRef<string | null>(null);
  const submissionGate = useRef(new SubmissionGate());
  const [url, setUrl] = useState("");
  const [fieldError, setFieldError] = useState<string | null>(null);
  const [uploadError, setUploadError] = useState<string | null>(null);
  const [busy, setBusy] = useState<BusyState>(null);
  const [source, setSource] = useState<SourceItemResponse | null>(null);
  const [file, setFile] = useState<File | null>(null);
  const [previewUrl, setPreviewUrl] = useState<string | null>(null);
  const [resume, setResume] = useState<ImageResumePoint | null>(null);
  const isBusy = busy !== null;

  useEffect(() => () => {
    if (previewObjectUrl.current) URL.revokeObjectURL(previewObjectUrl.current);
  }, []);

  function api() {
    return createSourceItemApi(window.location.origin);
  }

  async function submitUrl(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (isBusy || !submissionGate.current.tryEnter()) return;
    const validation = validateProductUrl(url);
    setFieldError(validation);
    setUploadError(null);
    if (validation) { submissionGate.current.leave(); return; }
    setBusy("url");
    setSource(null);
    setResume(null);
    try {
      const created = await api().createFromUrl(url.trim());
      setSource(created);
      if (created.status === "IMAGE_UPLOAD_REQUIRED" && created.action === "UPLOAD_IMAGE") {
        requestAnimationFrame(() => fileInput.current?.focus());
      }
    } catch (error) {
      setFieldError(sourceErrorMessage(error));
    } finally {
      setBusy(null);
      submissionGate.current.leave();
    }
  }

  function selectFile(selected: File | undefined) {
    if (!selected || isBusy) return;
    const validation = validateSourceImage(selected);
    setUploadError(validation);
    if (validation) {
      setFile(null);
      if (previewObjectUrl.current) URL.revokeObjectURL(previewObjectUrl.current);
      previewObjectUrl.current = null;
      setPreviewUrl(null);
      if (fileInput.current) fileInput.current.value = "";
      return;
    }
    if (previewObjectUrl.current) URL.revokeObjectURL(previewObjectUrl.current);
    previewObjectUrl.current = URL.createObjectURL(selected);
    setPreviewUrl(previewObjectUrl.current);
    setFile(selected);
    if (source?.status !== "IMAGE_UPLOAD_REQUIRED") setSource(null);
    setResume((current) => current?.uploaded ? null : current);
    setUploadError(null);
  }

  async function submitImage() {
    if (!file || isBusy || !submissionGate.current.tryEnter()) return;
    setUploadError(null);
    try {
      const completed = await uploadImageSource(api(), file, {
        fallbackSourceId: resume ? undefined : source?.status === "IMAGE_UPLOAD_REQUIRED" ? source.sourceItemId : undefined,
        resume,
        onPhase: setBusy,
      });
      setSource(completed);
      setResume(null);
    } catch (error) {
      const code = sourceErrorCode(error);
      if (error instanceof ImageWorkflowError) setResume(error.resume);
      if (code === "SOURCE_NOT_FOUND" || code === "SOURCE_REQUEST_FORBIDDEN") {
        setResume(null);
        setSource(null);
      }
      if (["SOURCE_IMAGE_INVALID", "SOURCE_IMAGE_TYPE_UNSUPPORTED", "SOURCE_IMAGE_TOO_LARGE", "SOURCE_UPLOAD_NOT_FOUND"].includes(code ?? "")) {
        if (previewObjectUrl.current) URL.revokeObjectURL(previewObjectUrl.current);
        previewObjectUrl.current = null;
        setPreviewUrl(null);
        setFile(null);
        setResume(null);
        if (fileInput.current) fileInput.current.value = "";
      }
      setUploadError(sourceErrorMessage(error));
    } finally {
      setBusy(null);
      submissionGate.current.leave();
    }
  }

  const statusMessage = busy ? busyMessages[busy] : source ? statusMessages[source.status] : null;
  const fallback = source?.status === "IMAGE_UPLOAD_REQUIRED";
  const success = source?.status === "READY_FOR_ANALYSIS";

  return (
    <div id="source-entry" className="rounded-panel border border-line bg-surface p-4 shadow-[0_24px_70px_rgba(23,54,45,0.09)] sm:p-5">
      <form onSubmit={submitUrl} noValidate>
        <div className="flex flex-col gap-2">
          <label htmlFor={inputId} className="text-sm font-semibold text-ink">{homepageContent.urlLabel}</label>
          <div className="flex flex-col gap-3 sm:flex-row">
            <input
              id={inputId}
              name="productUrl"
              type="url"
              inputMode="url"
              autoComplete="url"
              value={url}
              disabled={isBusy}
              onChange={(event) => { setUrl(event.target.value); setFieldError(null); if (success) setSource(null); }}
              placeholder={homepageContent.urlPlaceholder}
              aria-invalid={fieldError ? true : undefined}
              aria-describedby={`${helpId}${fieldError ? ` ${errorId}` : ""}`}
              className="min-h-13 min-w-0 flex-1 rounded-xl border border-line bg-canvas px-4 text-base text-ink placeholder:text-muted/70 hover:border-muted focus:border-ink disabled:cursor-wait disabled:opacity-65"
            />
            <button type="submit" disabled={isBusy || success} className="min-h-13 shrink-0 rounded-xl bg-ink px-6 font-semibold text-canvas transition-colors hover:bg-night disabled:cursor-not-allowed disabled:opacity-65">
              {busy === "url" ? "Checking…" : homepageContent.submitLabel}
            </button>
          </div>
          <p id={helpId} className="text-sm leading-6 text-muted">{homepageContent.urlHelp}</p>
          {fieldError && <p id={errorId} role="alert" className="rounded-xl border border-clay/30 bg-blush/45 px-4 py-3 text-sm leading-6 text-ink">{fieldError}</p>}
        </div>
      </form>

      <div className="my-5 flex items-center gap-3 text-xs font-bold tracking-[0.16em] text-muted" aria-hidden="true">
        <span className="h-px flex-1 bg-line" /> OR <span className="h-px flex-1 bg-line" />
      </div>

      {fallback && <p className="mb-4 rounded-xl border border-clay/30 bg-blush/45 px-4 py-3 text-sm leading-6 text-ink">{statusMessages.IMAGE_UPLOAD_REQUIRED}</p>}

      <div className="flex flex-col gap-3">
        <div className="flex flex-col gap-3 sm:flex-row sm:items-center">
          <input
            ref={fileInput}
            id={uploadId}
            name="outfitImage"
            type="file"
            accept="image/jpeg,image/png,image/webp"
            disabled={isBusy}
            className="peer sr-only"
            aria-describedby={`${statusId}${uploadError ? ` ${uploadErrorId}` : ""}`}
            onChange={(event) => selectFile(event.target.files?.[0])}
          />
          <label htmlFor={uploadId} className="inline-flex min-h-12 cursor-pointer items-center justify-center gap-2 rounded-xl border border-ink/20 bg-canvas px-5 text-sm font-semibold text-ink transition-colors hover:border-ink hover:bg-accent/40 peer-disabled:cursor-not-allowed peer-disabled:opacity-60 peer-focus-visible:outline-3 peer-focus-visible:outline-offset-3 peer-focus-visible:outline-focus">
            <UploadIcon />
            {file ? "Choose a different image" : homepageContent.uploadLabel}
          </label>
          {!file && <p className="text-sm leading-6 text-muted">{homepageContent.uploadHelp}</p>}
        </div>

        {file && previewUrl && (
          <div className="grid gap-4 rounded-2xl border border-line bg-canvas p-3 sm:grid-cols-[7rem_1fr] sm:items-center">
            {/* The local object URL is revoked on replacement or unmount. */}
            <Image src={previewUrl} alt={`Selected outfit preview: ${file.name}`} width={480} height={360} unoptimized className="aspect-[4/3] w-full rounded-xl object-cover sm:aspect-square" />
            <div className="min-w-0">
              <p className="truncate text-sm font-semibold text-ink">{file.name}</p>
              <p className="mt-1 text-sm leading-6 text-muted">Ready to upload securely.</p>
              <button type="button" onClick={submitImage} disabled={isBusy || success} className="mt-3 inline-flex min-h-11 items-center rounded-xl bg-clay px-5 text-sm font-semibold text-white hover:bg-clay/90 disabled:cursor-not-allowed disabled:opacity-60">
                {busy && busy !== "url" ? busyMessages[busy] : resume ? "Try upload again" : "Use this image"}
              </button>
            </div>
          </div>
        )}

        {uploadError && <p id={uploadErrorId} role="alert" className="rounded-xl border border-clay/30 bg-blush/45 px-4 py-3 text-sm leading-6 text-ink">{uploadError}</p>}
        <p id={statusId} role="status" aria-live="polite" className={`text-sm leading-6 ${success ? "font-semibold text-ink" : "text-muted"}`}>
          {statusMessage ?? "Choose either a product link or an outfit image."}
        </p>
        {success && source && (
          <button type="button" onClick={() => router.push(`/match?source=${encodeURIComponent(source.sourceItemId)}`)} className="min-h-12 rounded-xl bg-clay px-6 text-sm font-semibold text-white hover:bg-clay/90">
            Review outfit details
          </button>
        )}
      </div>
    </div>
  );
}

function UploadIcon() {
  return (
    <svg aria-hidden="true" viewBox="0 0 24 24" className="size-5 fill-none stroke-current" strokeWidth="1.8">
      <path d="M12 15V4m0 0L8 8m4-4 4 4M5 14v4.5A1.5 1.5 0 0 0 6.5 20h11a1.5 1.5 0 0 0 1.5-1.5V14" strokeLinecap="round" strokeLinejoin="round" />
    </svg>
  );
}
