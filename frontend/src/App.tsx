import { Footer } from './components/Footer';
import { Header } from './components/Header';
import { Hero } from './components/Hero';
import { HowItWorks } from './components/HowItWorks';
import { Monetization } from './components/Monetization';
import { Planner } from './components/Planner';
import { StatsStrip } from './components/StatsStrip';

export default function App() {
  return (
    <main>
      <Header />
      <Hero />
      <StatsStrip />
      <HowItWorks />
      <Planner />
      <Monetization />
      <Footer />
    </main>
  );
}
