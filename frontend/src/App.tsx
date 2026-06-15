import { Footer } from './components/Footer';
import { Header } from './components/Header';
import { HowItWorks } from './components/HowItWorks';
import { Monetization } from './components/Monetization';
import { Planner } from './components/Planner';
import { LocaleProvider } from './LocaleContext';

export default function App() {
  return (
    <LocaleProvider>
      <main>
        <Header />
        <Planner />
        <HowItWorks />
        <Monetization />
        <Footer />
      </main>
    </LocaleProvider>
  );
}
