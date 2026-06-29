// Sprint 10.148: a more creative BudgetSpace mark. The rounded square is the SPACE; the clay fill rising from the
// bottom is the BUDGET level filling it — "your space, filled to budget". White frame + clay fill on the ink tile.
// The fill is a path (rounded bottom corners matching the frame) so no clipPath / duplicate-id needed.
export function BrandMark({ className }: { className?: string }) {
  return (
    <span className={className ? `brand-mark ${className}` : 'brand-mark'} aria-hidden="true">
      <svg viewBox="0 0 32 32" width="21" height="21" fill="none" aria-hidden="true" focusable="false">
        {/* budget level filling the space (bottom ~40%), bottom corners rounded to match the frame */}
        <path d="M6.5 18 H25.5 V20.5 A5 5 0 0 1 20.5 25.5 H11.5 A5 5 0 0 1 6.5 20.5 Z" fill="#cf5f2a" />
        {/* the space: a rounded frame */}
        <rect x="6.5" y="6.5" width="19" height="19" rx="5" stroke="#fffdf8" strokeWidth="2.1" />
        {/* the budget line where the fill meets empty space */}
        <path d="M6.5 18 H25.5" stroke="#fffdf8" strokeWidth="2.1" strokeLinecap="round" />
      </svg>
    </span>
  );
}
