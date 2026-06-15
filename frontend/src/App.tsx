import { Footer } from './components/Footer';
import { Header } from './components/Header';
import { HowItWorks } from './components/HowItWorks';
import { Monetization } from './components/Monetization';
import { Planner } from './components/Planner';

export default function App() {
  return (
    <main>
      <Header />
      <Planner />
      <HowItWorks />
      <Monetization />
      <Footer />
    </main>
  );
}
