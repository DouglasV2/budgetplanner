// Sprint 10.142: a tiny inline line-icon set, replacing the room/beta EMOJI. Emoji in UI chrome is the strongest
// "quick AI build" tell; a consistent monochrome stroke set reads as crafted. No dependency (leaner than pulling
// lucide, which is itself the AI-default lib) — just a few hand-kept SVG paths. All inherit `currentColor`, so
// they recolour with the surrounding text (e.g. white on an active choice chip).
import type { RoomType, ProductCategory } from '../types';

interface IconProps {
  size?: number;
  className?: string;
}

function Svg({ size = 22, className, children }: IconProps & { children: React.ReactNode }) {
  return (
    <svg
      width={size}
      height={size}
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth={1.6}
      strokeLinecap="round"
      strokeLinejoin="round"
      className={className}
      aria-hidden="true"
      focusable="false"
    >
      {children}
    </svg>
  );
}

// Partial: dormant rooms (e.g. the de-scoped home-gym) fall back to the living-room glyph via RoomIcon below.
const ROOM_PATHS: Partial<Record<RoomType, React.ReactNode>> = {
  'living-room': (
    <>
      <path d="M5 10V8a2 2 0 0 1 2-2h10a2 2 0 0 1 2 2v2" />
      <rect x="3" y="10" width="18" height="7" rx="2" />
      <path d="M7 17v2M17 17v2" />
    </>
  ),
  bedroom: (
    <>
      <path d="M2 17v-5a2 2 0 0 1 2-2h16a2 2 0 0 1 2 2v5" />
      <path d="M6 10V8a1 1 0 0 1 1-1h4a1 1 0 0 1 1 1v2" />
      <path d="M2 17h20" />
      <path d="M2 17v2M22 17v2" />
    </>
  ),
  'home-office': (
    <>
      <rect x="3" y="4" width="18" height="11" rx="1.5" />
      <path d="M8 20h8M12 15v5" />
    </>
  ),
  'dining-room': (
    <>
      <circle cx="12" cy="12" r="8" />
      <circle cx="12" cy="12" r="3.2" />
    </>
  ),
  kitchen: (
    <>
      <path d="M4 9h16v5a4 4 0 0 1-4 4H8a4 4 0 0 1-4-4z" />
      <path d="M2.5 9h19" />
      <path d="M8 5.5c0-1 1-1 1-2M12 5.5c0-1 1-1 1-2M16 5.5c0-1 1-1 1-2" />
    </>
  ),
  hallway: (
    <>
      <path d="M6 21V4a1 1 0 0 1 1-1h10a1 1 0 0 1 1 1v17" />
      <path d="M4 21h16" />
      <circle cx="14" cy="12" r="1" />
    </>
  ),
  bathroom: (
    <>
      <path d="M4 12h16v4a3 3 0 0 1-3 3H7a3 3 0 0 1-3-3z" />
      <path d="M3 12h18" />
      <path d="M8 12V7a2 2 0 0 1 2-2h0a2 2 0 0 1 2 2" />
      <path d="M7 19l-1 2M18 19l1 2" />
    </>
  ),
  studio: (
    <>
      <rect x="3" y="4" width="18" height="16" rx="2" />
      <path d="M3 13h7v7M14 4v9h7" />
    </>
  )
};

export function RoomIcon({ room, size = 22, className }: IconProps & { room: RoomType }) {
  return <Svg size={size} className={className}>{ROOM_PATHS[room] ?? ROOM_PATHS['living-room']}</Svg>;
}

// A single restrained sparkle for the beta notice (replaces 🎉).
export function SparkIcon({ size = 18, className }: IconProps) {
  return (
    <Svg size={size} className={className}>
      <path d="M12 3l1.6 5.4L19 10l-5.4 1.6L12 17l-1.6-5.4L5 10l5.4-1.6z" />
    </Svg>
  );
}

// Sprint 10.162: a per-category furniture glyph, hand-drawn in the SAME stroke style as the room icons, used
// as the honest thumbnail when a product has no VERIFIED photo (an icon clearly isn't "the product photo",
// which a generic stock image would falsely imply). Every ProductCategory maps to a fitting piece — a couch
// for a sofa, a chest for a dresser, a trolley for a kitchen-cart — so the plan reads as crafted, not templated.
const CATEGORY_PATHS: Record<ProductCategory, React.ReactNode> = {
  sofa: (
    <>
      <path d="M5 10V8a2 2 0 0 1 2-2h10a2 2 0 0 1 2 2v2" />
      <rect x="3" y="10" width="18" height="6" rx="2" />
      <path d="M7 16v2M17 16v2" />
    </>
  ),
  chair: (
    <>
      <path d="M8 3v10l-1 7" />
      <path d="M8 13h8v7" />
    </>
  ),
  'dining-chair': (
    <>
      <path d="M8 3v10l-1 7" />
      <path d="M8 13h8v7" />
    </>
  ),
  table: (
    <>
      <path d="M3 8h18" />
      <path d="M6 8v6M18 8v6" />
    </>
  ),
  'dining-table': (
    <>
      <ellipse cx="12" cy="8" rx="9" ry="2" />
      <path d="M6 9v10M18 9v10" />
    </>
  ),
  'tv-unit': (
    <>
      <rect x="3" y="8" width="18" height="9" rx="1.5" />
      <path d="M3 12.5h18" />
      <path d="M7 10.4h2M15 10.4h2" />
      <path d="M6 17v2M18 17v2" />
    </>
  ),
  storage: (
    <>
      <rect x="5" y="3" width="14" height="18" rx="1.5" />
      <path d="M5 9h14M5 15h14" />
    </>
  ),
  'kitchen-storage': (
    <>
      <path d="M4 6h16M4 12h16M4 18h16" />
      <path d="M6 6v12M18 6v12" />
    </>
  ),
  'kitchen-cart': (
    <>
      <rect x="5" y="5" width="14" height="11" rx="1.5" />
      <path d="M5 10.5h14" />
      <path d="M8 16v1M16 16v1" />
      <circle cx="8" cy="18.5" r="1.4" />
      <circle cx="16" cy="18.5" r="1.4" />
    </>
  ),
  // Sprint 10.175: a modular kitchen set — base cabinets under a continuous worktop with an upper cabinet.
  'kitchen-set': (
    <>
      <path d="M3 8h18" />
      <rect x="3" y="8" width="18" height="11" rx="1" />
      <path d="M9 8v11M15 8v11" />
      <path d="M6 12.5h1M12 12.5h1M18 12.5h1" />
      <rect x="14" y="3" width="6" height="4" rx="0.6" />
    </>
  ),
  // Sprint 10.176: kitchen appliances.
  oven: (
    <>
      <rect x="4" y="4" width="16" height="16" rx="1.5" />
      <path d="M4 9h16" />
      <path d="M7 6.5h2M15 6.5h2" />
      <rect x="7" y="11.5" width="10" height="6" rx="0.8" />
    </>
  ),
  hob: (
    <>
      <rect x="4" y="4" width="16" height="16" rx="1.5" />
      <circle cx="9" cy="9" r="2" />
      <circle cx="15" cy="9" r="2" />
      <circle cx="9" cy="15" r="2" />
      <circle cx="15" cy="15" r="2" />
    </>
  ),
  'cooker-hood': (
    <>
      <path d="M3 10l3-4h12l3 4z" />
      <path d="M3 10h18" />
      <path d="M8 10v3M16 10v3" />
    </>
  ),
  fridge: (
    <>
      <rect x="6" y="3" width="12" height="18" rx="1.5" />
      <path d="M6 10h12" />
      <path d="M9 6v2M9 12.5v3" />
    </>
  ),
  freezer: (
    <>
      <rect x="4" y="6" width="16" height="12" rx="1.5" />
      <path d="M12 6v12M4 12h16" />
      <path d="M7.5 9h1M15.5 9h1M7.5 15h1M15.5 15h1" />
    </>
  ),
  dishwasher: (
    <>
      <rect x="5" y="3" width="14" height="18" rx="1.5" />
      <path d="M5 7h14" />
      <path d="M8 5h4" />
      <circle cx="12" cy="14" r="3.5" />
    </>
  ),
  microwave: (
    <>
      <rect x="3" y="6" width="18" height="12" rx="1.5" />
      <rect x="5.5" y="8.5" width="9" height="7" rx="0.8" />
      <path d="M17 9v1M17 12v1M17 15v1" />
    </>
  ),
  rug: (
    <>
      <rect x="3" y="7" width="18" height="10" rx="1.5" />
      <rect x="6.5" y="10" width="11" height="4" rx="1" />
    </>
  ),
  lighting: (
    <>
      <path d="M12 3v4" />
      <path d="M6 13a6 6 0 0 1 12 0z" />
      <path d="M10 16h4" />
    </>
  ),
  decor: (
    <>
      <rect x="4" y="4" width="16" height="16" rx="1.5" />
      <path d="M4 16l4-4 3 3 5-6 4 5" />
      <circle cx="9" cy="9" r="1.2" />
    </>
  ),
  desk: (
    <>
      <path d="M3 8h18" />
      <path d="M4 8v11M20 8v11" />
      <path d="M13 8v6h7" />
      <path d="M15 11h3" />
    </>
  ),
  bed: (
    <>
      <path d="M3 17v-5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2v5" />
      <path d="M3 17h18" />
      <path d="M6 10V8a1 1 0 0 1 1-1h4a1 1 0 0 1 1 1v2" />
      <path d="M3 17v2M21 17v2" />
    </>
  ),
  mattress: (
    <>
      <rect x="3" y="8" width="18" height="8" rx="3" />
      <path d="M7 11v2M12 11v2M17 11v2" />
    </>
  ),
  nightstand: (
    <>
      <rect x="7" y="6" width="10" height="12" rx="1.5" />
      <path d="M7 11h10" />
      <path d="M11 8.5h2M11 14h2" />
      <path d="M8 18v2M16 18v2" />
    </>
  ),
  wardrobe: (
    <>
      <rect x="5" y="3" width="14" height="18" rx="1.5" />
      <path d="M12 3v18" />
      <path d="M10 11v2M14 11v2" />
    </>
  ),
  dresser: (
    <>
      <rect x="4" y="5" width="16" height="13" rx="1.5" />
      <path d="M4 9.5h16M4 14h16" />
      <path d="M10.5 7h3M10.5 11.5h3M10.5 16h3" />
      <path d="M6 18v2M18 18v2" />
    </>
  ),
  textiles: (
    <>
      <rect x="5" y="6" width="14" height="12" rx="4" />
      <path d="M6 7l-1.5-1.5M18 7l1.5-1.5M6 17l-1.5 1.5M18 17l1.5 1.5" />
    </>
  ),
  'gym-equipment': (
    <>
      <path d="M4 9v6M20 9v6" />
      <path d="M7 7v10M17 7v10" />
      <path d="M7 12h10" />
    </>
  ),
  // Sprint 10.169: bathroom fixtures (Pevex HR).
  toilet: (
    <>
      <path d="M8 4h5v5H8z" />
      <path d="M6 9h9c0 3-1.6 6-4.5 6S6 12 6 9z" />
      <path d="M9.5 15v3H6.5" />
    </>
  ),
  washbasin: (
    <>
      <path d="M4 11h16" />
      <path d="M5 11c0 3.4 3 5.5 7 5.5s7-2.1 7-5.5" />
      <path d="M12 5v3M12 5h2.5" />
    </>
  ),
  'bath-shower': (
    <>
      <path d="M4 12h16v3a3 3 0 0 1-3 3H7a3 3 0 0 1-3-3z" />
      <path d="M7 18l-1 2M17 18l1 2" />
      <path d="M9 12V7a2 2 0 0 1 2-2" />
      <circle cx="11" cy="4.6" r="0.7" />
    </>
  )
};

export function CategoryIcon({ category, size = 22, className }: IconProps & { category: ProductCategory }) {
  return <Svg size={size} className={className}>{CATEGORY_PATHS[category] ?? CATEGORY_PATHS.decor}</Svg>;
}

// Small action glyphs for the plan rows (replace the ⇄ / × unicode chars, which read as a quick build).
export function SwapIcon({ size = 16, className }: IconProps) {
  return (
    <Svg size={size} className={className}>
      <path d="M4 8h13l-3-3M20 16H7l3 3" />
    </Svg>
  );
}

export function CloseIcon({ size = 16, className }: IconProps) {
  return (
    <Svg size={size} className={className}>
      <path d="M6 6l12 12M18 6L6 18" />
    </Svg>
  );
}

export function ExternalLinkIcon({ size = 15, className }: IconProps) {
  return (
    <Svg size={size} className={className}>
      <path d="M14 4h6v6" />
      <path d="M20 4l-8 8" />
      <path d="M18 13v5a2 2 0 0 1-2 2H6a2 2 0 0 1-2-2V8a2 2 0 0 1 2-2h5" />
    </Svg>
  );
}

// Sprint 10.183 (Move-In QoL): a padlock for the "keep this product/room" affordance — a kept piece is pinned
// against whole-plan adjustments (matches the single-room lock semantics).
export function LockIcon({ size = 16, className }: IconProps) {
  return (
    <Svg size={size} className={className}>
      <rect x="5" y="11" width="14" height="9" rx="2" />
      <path d="M8 11V8a4 4 0 0 1 8 0v3" />
    </Svg>
  );
}
