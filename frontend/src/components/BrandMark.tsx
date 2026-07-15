// SEO sprint 2026-07-15: the brand mark is now the owner-supplied BudgetSpace logo (public/budgetspacelogo.png),
// replacing the drawn "settee" SVG. Kept as a shared component so every placement (header, sign-in gate) stays in
// sync. The image sits in public/ so the static SEO landing pages reference the same file (/budgetspacelogo.png).
// Intrinsic size 127×86; width/height hint the aspect ratio (the .brand-mark rule scales it by height).
export function BrandMark({ className }: { className?: string }) {
  return (
    <img
      className={className ? `brand-mark ${className}` : 'brand-mark'}
      src="/budgetspacelogo.png"
      alt=""
      aria-hidden="true"
      width={53}
      height={36}
    />
  );
}
