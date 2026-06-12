import { Footer } from './components/Footer';
import { Header } from './components/Header';
import { HowItWorks } from './components/HowItWorks';
import { Planner } from './components/Planner';

export default function App() {
  return (
    <main>
      <Header />
      <Planner />
      <HowItWorks />
      <Footer />
    </main>
  );
}
