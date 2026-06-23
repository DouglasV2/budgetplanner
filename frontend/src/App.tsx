import { AuthGate } from './components/AuthGate';
import { Footer } from './components/Footer';
import { Header } from './components/Header';
import { HowItWorks } from './components/HowItWorks';
import { Monetization } from './components/Monetization';
import { Planner } from './components/Planner';
import { AuthProvider, useAuth } from './AuthContext';
import { LocaleProvider } from './LocaleContext';

// A shared plan link (/plan/<id>) must open without a sign-in wall — that recipient may be a logged-out
// visitor, and the share growth loop depends on it. So the front door never gates these.
function isSharedPlanLink() {
  return /^\/plan\/[^/]+$/.test(window.location.pathname);
}

function AppShell() {
  const { user, loading, guestContinued, plusEnabled } = useAuth();
  const shared = isSharedPlanLink();
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
      <Planner />
      <HowItWorks />
      {/* Sprint 10.103: the Plus/pricing section is hidden during the free beta (plusEnabled=false from the
          backend). Flip BUDGETSPACE_PLUS_ENABLED=true to bring it back — the component is untouched. */}
      {plusEnabled && <Monetization />}
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
