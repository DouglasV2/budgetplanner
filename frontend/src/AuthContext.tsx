// Sprint 10.63: app-wide auth. Holds the signed-in user (or null for a guest), whether Google sign-in is
// available + its public client id, and the "continue as guest" choice that lets a visitor past the front door.
import { createContext, useContext, useEffect, useMemo, useState, type ReactNode } from 'react';
import { authLogout, fetchAuthMe, type AuthUser } from './api/client';

const GUEST_KEY = 'bs-guest-continue';

interface AuthContextValue {
  user: AuthUser | null;
  googleEnabled: boolean;
  googleClientId: string | null;
  loading: boolean;
  guestContinued: boolean;
  onSignedIn: (user: AuthUser) => void;
  continueAsGuest: () => void;
  openSignIn: () => void;
  signOut: () => Promise<void>;
}

const AuthContext = createContext<AuthContextValue | null>(null);

function readGuestContinued(): boolean {
  try {
    return window.sessionStorage.getItem(GUEST_KEY) === '1';
  } catch {
    return false;
  }
}

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<AuthUser | null>(null);
  const [googleEnabled, setGoogleEnabled] = useState(false);
  const [googleClientId, setGoogleClientId] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  const [guestContinued, setGuestContinued] = useState<boolean>(readGuestContinued);

  // On load, ask the backend who is signed in (the session is an HttpOnly cookie we can't read directly).
  // A failure degrades to "logged-out guest" so the app still works if the auth endpoint is unavailable.
  useEffect(() => {
    let cancelled = false;
    void fetchAuthMe()
      .then((me) => {
        if (cancelled) return;
        setUser(me.user);
        setGoogleEnabled(me.googleEnabled);
        setGoogleClientId(me.googleClientId);
      })
      .catch(() => undefined)
      .finally(() => {
        if (!cancelled) setLoading(false);
      });
    return () => { cancelled = true; };
  }, []);

  function setGuest(value: boolean) {
    setGuestContinued(value);
    try {
      if (value) window.sessionStorage.setItem(GUEST_KEY, '1');
      else window.sessionStorage.removeItem(GUEST_KEY);
    } catch {
      // sessionStorage unavailable (private mode) — the in-memory state still gates this page load.
    }
  }

  const value = useMemo<AuthContextValue>(() => ({
    user,
    googleEnabled,
    googleClientId,
    loading,
    guestContinued,
    onSignedIn: (next: AuthUser) => setUser(next),
    continueAsGuest: () => setGuest(true),
    openSignIn: () => setGuest(false),
    signOut: async () => {
      try {
        await authLogout();
      } catch {
        // Even if the network call fails, drop the local user so the UI reflects signed-out immediately.
      }
      setUser(null);
      // Keep them in the app as a guest after signing out, rather than bouncing back to the front door.
      setGuest(true);
    }
  }), [user, googleEnabled, googleClientId, loading, guestContinued]);

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthContextValue {
  const ctx = useContext(AuthContext);
  if (!ctx) {
    throw new Error('useAuth must be used within an AuthProvider');
  }
  return ctx;
}
