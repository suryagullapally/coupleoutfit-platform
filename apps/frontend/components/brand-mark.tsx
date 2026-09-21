export function BrandMark() {
  return (
    <span className="flex items-center gap-2.5">
      <span aria-hidden="true" className="relative flex size-9 items-center justify-center rounded-full bg-ink text-canvas">
        <span className="absolute left-[9px] size-3 rounded-full border border-current" />
        <span className="absolute right-[9px] size-3 rounded-full border border-current" />
      </span>
      <span className="text-[1.05rem] font-semibold tracking-[-0.035em]">CoupleOutfit</span>
    </span>
  );
}
