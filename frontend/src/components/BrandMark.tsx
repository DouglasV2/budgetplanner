// Sprint 10.164: "furnish the space" mark — a considered 2-seat settee inside the ink tile, resting on a cream
// floor line (the room). Evolves the old flat "budget fills a space" level into the product actually in the space.
// Detailed but still abstract: split back + seat cushions (a "+" seam = a 2-seater), track arms, and thin splayed
// mid-century legs that lift it off the floor so it reads as designed furniture, not a generic couch glyph. Clay
// stays the hero colour, tying the mark to the wordmark's clay "space" and the header's clay budget-fill line.
export function BrandMark({ className }: { className?: string }) {
  return (
    <span className={className ? `brand-mark ${className}` : 'brand-mark'} aria-hidden="true">
      <svg viewBox="0 0 32 32" width="22" height="22" fill="none" aria-hidden="true" focusable="false">
        {/* the floor of the room the piece sits in */}
        <path d="M5 22.8 H27" stroke="#f7f1e7" strokeWidth="1.6" strokeLinecap="round" opacity="0.8" />
        {/* thin splayed legs — lifts it off the floor (mid-century stance) */}
        <g stroke="#cf5f2a" strokeWidth="1.7" strokeLinecap="round">
          <path d="M9 19 L7.9 22.3" />
          <path d="M13.2 19 L12.7 22.3" />
          <path d="M18.8 19 L19.3 22.3" />
          <path d="M23 19 L24.1 22.3" />
        </g>
        {/* back rest (rises above the arms) and the seat */}
        <rect x="8" y="8" width="16" height="6" rx="2.2" fill="#cf5f2a" />
        <rect x="8" y="14" width="16" height="5" rx="2" fill="#cf5f2a" />
        {/* track arms */}
        <rect x="5.5" y="11.5" width="3" height="7" rx="1.5" fill="#cf5f2a" />
        <rect x="23.5" y="11.5" width="3" height="7" rx="1.5" fill="#cf5f2a" />
        {/* cushion seams: a "+" that reads the body as two back + two seat cushions */}
        <g stroke="#211e19" strokeWidth="1" strokeLinecap="round">
          <path d="M9 14 H23" />
          <path d="M16 8.8 V18.2" />
        </g>
      </svg>
    </span>
  );
}
