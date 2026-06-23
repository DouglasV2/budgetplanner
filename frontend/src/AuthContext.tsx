// Sprint 10.63 → 10.69: app-wide auth + billing state. Holds the signed-in user (or null for a guest), whether
// Google sign-in / Stripe billing are available, the "continue as guest" choice, and the post-checkout upgrade.
import { createContext, useContext, useEffect, useMemo, useState, type ReactNode } from 'react';
import { authLogout, confirmCheckout, deleteAccount as deleteAccountApi, fetchAuthMe, type AuthUser } from './api/client';

const GUEST_KEY = 'bs-guest-continue';

interface AuthContextValue {
  user: AuthUser | null;
  googleEnabled: boolean;
  googleClientId: string | null;
  billingEnabled: boolean;
  aiEnabled: boolean;
  plusEnabled: boolean;
  loading: boolean;
  guestContinued: boolean;
  justUpgraded: boolean;
  onSignedIn: (user: AuthUser) => void;
  continueAsGuest: () => void;
  openSignIn: () => void;
  signOut: () => Promise<void>;
  deleteAccount: () => Promise<void>;
}

const AuthContext = createContext<AuthContextValue | null>(null);

function readGuestContinued(): boolean {
  try {
    return window.sessionStorage.getItem(GUEST_KEY) === '1';
  } catch {
    return false;
  }
}

// Strip the Stripe-return params so a refresh doesn't re-confirm and the URL stays clean.
function cleanPlusParams() {
  const url = new URL(window.location.href);
  url.searchParams.delete('plus');
  url.searchParams.delete('session_id');
  window.history.replaceState({}, '', url.pathname + url.search + url.hash);
}

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<AuthUser | null>(null);
  const [googleEnabled, setGoogleEnabled] = useState(false);
  const [googleClientId, setGoogleClientId] = useState<string | null>(null);
  const [billingEnabled, setBillingEnabled] = useState(false);
  const [aiEnabled, setAiEnabled] = useState(false);
  const [plusEnabled, setPlusEnabled] = useState(false);
  const [loading, setLoading] = useState(true);
  const [guestContinued, setGuestContinued] = useState<boolean>(readGuestContinued);
  const [justUpgraded, setJustUpgraded] = useState(false);

  // On load, ask the backend who is signed in (the session is an HttpOnly cookie we can't read directly), then —
  // if we just came back from Stripe Checkout — verify the session and reflect the Plus upgrade. A failure
  // degrades to "logged-out guest" so the app still works if the auth endpoint is unavailable.
  useEffect(() => {
    let cancelled = false;
    async function init() {
      let me;
      try {
        me = await fetchAuthMe();
      } catch {
        if (!cancelled) setLoading(false);
        return;
      }
      if (cancelled) return;
      setUser(me.user);
      setGoogleEnabled(me.googleEnabled);
      setGoogleClientId(me.googleClientId);
      setBillingEnabled(me.billingEnabled);
      setAiEnabled(me.aiEnabled);
      setPlusEnabled(me.plusEnabled);

      const params = new URLSearchParams(window.location.search);
      const plus = params.get('plus');
      const sessionId = params.get('session_id');
      if (plus === 'success' && sessionId) {
        try {
          const result = await confirmCheckout(sessionId);
          if (!cancelled && result.plan === 'PLUS') {
            const refreshed = await fetchAuthMe();
            if (!cancelled) {
              setUser(refreshed.user);
              setJustUpgraded(true);
            }
          }
        } catch {
          // Confirm failed — leave the account as-is; the webhook is the backstop, and they can retry from pricing.
        }
      }
      if (plus) cleanPlusParams();
      if (!cancelled) setLoading(false);
    }
    void init();
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
    billingEnabled,
    aiEnabled,
    plusEnabled,
    loading,
    guestContinued,
    justUpgraded,
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
    },
    // Sprint 10.72: GDPR erasure. The backend deletes the account + data and clears the cookie; reflect it
    // locally by dropping the user and staying in the app as a guest. Errors propagate so the dialog can show them.
    deleteAccount: async () => {
      await deleteAccountApi();
      setUser(null);
      setGuest(true);
    }
  }), [user, googleEnabled, googleClientId, billingEnabled, aiEnabled, plusEnabled, loading, guestContinued, justUpgraded]);

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthContextValue {
  const ctx = useContext(AuthContext);
  if (!ctx) {
    throw new Error('useAuth must be used within an AuthProvider');
  }
  return ctx;
}
