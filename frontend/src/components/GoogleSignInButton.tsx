// Sprint 10.63: the real "Sign in with Google" button, via Google Identity Services (GIS). GIS hands us a
// signed ID token (the `credential`); we send it to the backend, which verifies it and starts a session.
// The button only renders on an origin registered in the OAuth client's Authorized JavaScript origins.
import { useEffect, useRef } from 'react';
import { googleLogin } from '../api/client';
import { useAuth } from '../AuthContext';
import { useLocale } from '../LocaleContext';

interface GisId {
  initialize: (config: { client_id: string; callback: (response: { credential?: string }) => void }) => void;
  renderButton: (parent: HTMLElement, options: Record<string, unknown>) => void;
}
interface GisWindow {
  google?: { accounts?: { id?: GisId } };
}

const GIS_SRC = 'https://accounts.google.com/gsi/client';
let gisPromise: Promise<void> | null = null;

function loadGis(): Promise<void> {
  if ((window as unknown as GisWindow).google?.accounts?.id) return Promise.resolve();
  if (gisPromise) return gisPromise;
  gisPromise = new Promise<void>((resolve, reject) => {
    const script = document.createElement('script');
    script.src = GIS_SRC;
    script.async = true;
    script.onload = () => resolve();
    script.onerror = () => reject(new Error('Google Identity Services failed to load'));
    document.head.appendChild(script);
  });
  return gisPromise;
}

export function GoogleSignInButton({ clientId, onError }: { clientId: string; onError: (message: string) => void }) {
  const { onSignedIn } = useAuth();
  const { t } = useLocale();
  const mountRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    let cancelled = false;
    void loadGis()
      .then(() => {
        if (cancelled || !mountRef.current) return;
        const id = (window as unknown as GisWindow).google?.accounts?.id;
        if (!id) {
          onError(t('auth.signInError'));
          return;
        }
        id.initialize({
          client_id: clientId,
          callback: (response) => {
            if (!response.credential) {
              onError(t('auth.signInError'));
              return;
            }
            void googleLogin(response.credential)
              .then((user) => onSignedIn(user))
              .catch(() => onError(t('auth.signInError')));
          }
        });
        mountRef.current.innerHTML = '';
        id.renderButton(mountRef.current, {
          theme: 'filled_blue',
          size: 'large',
          shape: 'pill',
          text: 'continue_with',
          logo_alignment: 'left',
          width: 300
        });
      })
      .catch(() => {
        if (!cancelled) onError(t('auth.signInError'));
      });
    return () => { cancelled = true; };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [clientId]);

  return <div ref={mountRef} className="google-signin-mount" aria-label="Google" />;
}
