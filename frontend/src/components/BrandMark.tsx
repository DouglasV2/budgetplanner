// Sprint 10.147: a dedicated BudgetSpace logo mark, replacing the plain font "B". It's a small room/frame with
// a clay "furniture piece" sitting on the floor line — i.e. a furnished space within a frame (space + budget),
// drawn white + clay on the existing dark ink tile. Used in the header and the sign-in gate.
export function BrandMark({ className }: { className?: string }) {
  return (
    <span className={className ? `brand-mark ${className}` : 'brand-mark'} aria-hidden="true">
      <svg viewBox="0 0 32 32" width="21" height="21" fill="none" aria-hidden="true" focusable="false">
        {/* the "space": a rounded frame */}
        <rect x="6.5" y="6.5" width="19" height="19" rx="5" stroke="#fffdf8" strokeWidth="2.1" />
        {/* the floor line */}
        <path d="M6.5 19.4h19" stroke="#fffdf8" strokeWidth="2.1" strokeLinecap="round" />
        {/* a furniture piece on the floor — the clay brand accent */}
        <rect x="15.6" y="20.6" width="7.6" height="4" rx="1.3" fill="#cf5f2a" />
      </svg>
    </span>
  );
}
