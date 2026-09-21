"use client";

import { useId, useState, type FormEvent } from "react";
import type { AnalysisRevision, ClothingAttributes } from "./types";
import { categories, colors, details, fits, formalities, materials, patterns, seasons, styles, subcategories, tokenLabel } from "./vocabulary";
import { cloneAttributes, validateAttributes, type AttributeErrors } from "./workflow";

export function AttributeSummary({ revision }: { revision: AnalysisRevision }) {
  const attributes = revision.attributes;
  return (
    <div className="space-y-6">
      <dl className="grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
        <Value label="Garment" value={tokenLabel(attributes.category)} />
        <Value label="Type" value={attributes.subcategory ? tokenLabel(attributes.subcategory) : "Not specified"} />
        <Value label="Pattern" value={tokenLabel(attributes.pattern)} />
        <Value label="Fit" value={tokenLabel(attributes.fit)} />
        <Value label="Formality" value={tokenLabel(attributes.formality)} />
      </dl>
      <TokenGroup label="Main colours" values={attributes.primaryColors} />
      <TokenGroup label="Secondary colours" values={attributes.secondaryColors} />
      <TokenGroup label="Material appearance" values={attributes.materialAppearance} />
      <TokenGroup label="Season" values={attributes.seasonality} />
      <TokenGroup label="Style" values={attributes.styleTags} />
      <TokenGroup label="Visible details" values={attributes.details} />
    </div>
  );
}

function Value({ label, value }: { label: string; value: string }) {
  return <div className="rounded-2xl border border-line bg-canvas px-4 py-3"><dt className="text-xs font-bold uppercase tracking-[0.13em] text-muted">{label}</dt><dd className="mt-1 text-base font-semibold text-ink">{value}</dd></div>;
}

function TokenGroup({ label, values }: { label: string; values: readonly string[] }) {
  return (
    <div>
      <h3 className="text-sm font-semibold text-ink">{label}</h3>
      <div className="mt-2 flex flex-wrap gap-2">
        {values.length ? values.map((value) => <span key={value} className="rounded-full border border-line bg-canvas px-3 py-1.5 text-sm text-ink">{tokenLabel(value)}</span>) : <span className="text-sm text-muted">Not identified</span>}
      </div>
    </div>
  );
}

export function AttributeEditor({ revision, saving, submitError, onCancel, onSave }: {
  revision: AnalysisRevision;
  saving: boolean;
  submitError: string | null;
  onCancel(): void;
  onSave(attributes: ClothingAttributes): Promise<void>;
}) {
  const [draft, setDraft] = useState(() => cloneAttributes(revision.attributes));
  const [errors, setErrors] = useState<AttributeErrors>({});

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const nextErrors = validateAttributes(draft);
    setErrors(nextErrors);
    if (Object.keys(nextErrors).length) return;
    await onSave(draft);
  }

  function setList(field: "primaryColors" | "secondaryColors" | "materialAppearance" | "seasonality" | "styleTags" | "details", value: string, checked: boolean) {
    const current = draft[field];
    const next = checked ? [...current, value] : current.filter((item) => item !== value);
    setDraft({ ...draft, [field]: next });
    setErrors((existing) => ({ ...existing, [field]: undefined }));
  }

  return (
    <form onSubmit={submit} className="space-y-7" noValidate>
      <div className="grid gap-4 sm:grid-cols-2">
        <SelectField label="Garment" value={draft.category} options={categories} onChange={(value) => setDraft({ ...draft, category: value as ClothingAttributes["category"] })} />
        <SelectField label="Type" value={draft.subcategory ?? ""} options={subcategories} allowEmpty onChange={(value) => setDraft({ ...draft, subcategory: value || null })} />
        <SelectField label="Pattern" value={draft.pattern} options={patterns} onChange={(value) => setDraft({ ...draft, pattern: value as ClothingAttributes["pattern"] })} />
        <SelectField label="Fit" value={draft.fit} options={fits} onChange={(value) => setDraft({ ...draft, fit: value as ClothingAttributes["fit"] })} />
        <SelectField label="Formality" value={draft.formality} options={formalities} onChange={(value) => setDraft({ ...draft, formality: value as ClothingAttributes["formality"] })} />
      </div>

      <ChoiceGroup label="Main colours" hint="Choose up to 3" values={draft.primaryColors} options={colors} error={errors.primaryColors} onChange={(value, checked) => setList("primaryColors", value, checked)} />
      <ChoiceGroup label="Secondary colours" hint="Choose up to 3, different from the main colours" values={draft.secondaryColors} options={colors} error={errors.secondaryColors} onChange={(value, checked) => setList("secondaryColors", value, checked)} />
      <ChoiceGroup label="Material appearance" hint="Choose up to 4" values={draft.materialAppearance} options={materials} error={errors.materialAppearance} onChange={(value, checked) => setList("materialAppearance", value, checked)} />
      <ChoiceGroup label="Season" hint="Choose up to 4" values={draft.seasonality} options={seasons} error={errors.seasonality} onChange={(value, checked) => setList("seasonality", value, checked)} />
      <ChoiceGroup label="Style" hint="Choose up to 8" values={draft.styleTags} options={styles} error={errors.styleTags} onChange={(value, checked) => setList("styleTags", value, checked)} />
      <ChoiceGroup label="Visible details" hint="Choose up to 10" values={draft.details} options={details} error={errors.details} onChange={(value, checked) => setList("details", value, checked)} />

      {submitError && <p role="alert" className="rounded-xl border border-clay/30 bg-blush/45 px-4 py-3 text-sm leading-6 text-ink">{submitError}</p>}
      <div className="flex flex-col-reverse gap-3 sm:flex-row sm:justify-end">
        <button type="button" onClick={onCancel} disabled={saving} className="min-h-12 rounded-xl border border-ink/20 px-5 text-sm font-semibold text-ink hover:bg-canvas disabled:opacity-60">Cancel</button>
        <button type="submit" disabled={saving} className="min-h-12 rounded-xl bg-ink px-6 text-sm font-semibold text-canvas hover:bg-night disabled:cursor-wait disabled:opacity-60">{saving ? "Saving corrections…" : "Save corrections"}</button>
      </div>
    </form>
  );
}

function SelectField({ label, value, options, allowEmpty = false, onChange }: { label: string; value: string; options: readonly string[]; allowEmpty?: boolean; onChange(value: string): void }) {
  return (
    <label className="grid gap-2 text-sm font-semibold text-ink">
      {label}
      <select value={value} onChange={(event) => onChange(event.target.value)} className="min-h-12 rounded-xl border border-line bg-canvas px-3 text-base font-normal text-ink">
        {allowEmpty && <option value="">Not specified</option>}
        {options.map((option) => <option key={option} value={option}>{tokenLabel(option)}</option>)}
      </select>
    </label>
  );
}

function ChoiceGroup({ label, hint, values, options, error, onChange }: {
  label: string;
  hint: string;
  values: readonly string[];
  options: readonly string[];
  error?: string;
  onChange(value: string, checked: boolean): void;
}) {
  const errorId = useId();
  return (
    <fieldset aria-invalid={error ? true : undefined} aria-describedby={error ? errorId : undefined} className="min-w-0">
      <legend className="text-sm font-semibold text-ink">{label}</legend>
      <p className="mt-1 text-sm text-muted">{hint}</p>
      <div className="mt-3 flex flex-wrap gap-2">
        {options.map((option) => {
          const checked = values.includes(option);
          return (
            <label key={option} className={`cursor-pointer rounded-full border px-3 py-2 text-sm transition-colors has-focus-visible:outline-3 has-focus-visible:outline-offset-3 has-focus-visible:outline-focus ${checked ? "border-ink bg-ink text-canvas" : "border-line bg-canvas text-ink hover:border-muted"}`}>
              <input type="checkbox" className="sr-only" checked={checked} onChange={(event) => onChange(option, event.target.checked)} />
              {tokenLabel(option)}
            </label>
          );
        })}
      </div>
      {error && <p id={errorId} role="alert" className="mt-2 text-sm font-medium text-clay">{error}</p>}
    </fieldset>
  );
}
