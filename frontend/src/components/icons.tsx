// Sprint 10.142: a tiny inline line-icon set, replacing the room/beta EMOJI. Emoji in UI chrome is the strongest
// "quick AI build" tell; a consistent monochrome stroke set reads as crafted. No dependency (leaner than pulling
// lucide, which is itself the AI-default lib) — just a few hand-kept SVG paths. All inherit `currentColor`, so
// they recolour with the surrounding text (e.g. white on an active choice chip).
import type { RoomType } from '../types';

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
