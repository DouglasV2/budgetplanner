import { useEffect } from 'react';
import { AuthGate } from './components/AuthGate';
import { Footer } from './components/Footer';
import { Header } from './components/Header';
import { HowItWorks } from './components/HowItWorks';
import { PlannerHero } from './components/PlannerHero';
import { Planner } from './components/Planner';
import { AuthProvider, useAuth } from './AuthContext';
import { LocaleProvider } from './LocaleContext';

// A shared plan link (/plan/<id>) must open without a sign-in wall — that recipient may be a logged-out
// visitor, and the share growth loop depends on it. So the front door never gates these.
function isSharedPlanLink() {
  return /^\/plan\/[^/]+$/.test(window.location.pathname);
}

function AppShell() {
  const { user, loading, guestContinued } = useAuth();
  const shared = isSharedPlanLink();
  // SEO sprint: shared plans stay reachable by anyone with the link but must not be indexed. The authoritative
  // control is the nginx `X-Robots-Tag: noindex` header on /plan/:id; this flips the document's robots meta to
  // noindex as a client-side defence for a JS-rendering crawler. The homepage keeps index,follow from index.html.
  useEffect(() => {
    if (!shared) return;
    document.querySelector('meta[name="robots"]')?.setAttribute('content', 'noindex, nofollow');
  }, [shared]);
  // Returning guests and shared-link recipients are decided synchronously (sessionStorage / pathname), so they
  // render immediately. Only a truly-undecided first visit waits for the /me round-trip — showing a neutral
  // splash rather than flashing the whole app and then slamming the front door over it.
  const decided = !loading || guestContinued || shared;
  const showGate = decided && !user && !guestContinued && !shared;

  if (!decided) {
    return <div className="auth-splash" aria-hidden="true" />;
  }

  return (
    <main>
      <Header />
      {/* Sprint 10.182: header hero band (owner mockup) — three-beat headline + subtitle + new-plan / my-plans
          actions + a drop-in visual. Replaces the slim PlannerSubnav strip; beta state is read inside. */}
      <PlannerHero />
      <Planner />
      <HowItWorks />
      <Footer />
      {showGate && <AuthGate />}
    </main>
  );
}

export default function App() {
  return (
    <LocaleProvider>
      <AuthProvider>
        <AppShell />
      </AuthProvider>
    </LocaleProvider>
  );
}
