// Sprint 10.189 multilingual synonym audit (2026-07-20): the Move-In nudge only fires when the free-text prompt
// names 2+ rooms (or a whole apartment). Each case below is a real native prompt that used to slip through because
// a market's own room word was missing (so the single-room planner silently under-served a whole-apartment ask).
import { describe, expect, it } from 'vitest';
import { detectMultiRoom } from './multiRoom';

describe('detectMultiRoom — multilingual room detection', () => {
  const roomsOf = (prompt: string) => detectMultiRoom(prompt)?.rooms ?? [];

  it('detects two rooms across every market language', () => {
    expect(roomsOf('sisusta olohuone ja keittio')).toEqual(expect.arrayContaining(['living-room', 'kitchen']));       // FI
    expect(roomsOf('je veux amenager ma cuisine et le salon')).toEqual(expect.arrayContaining(['kitchen', 'living-room'])); // FR
    expect(roomsOf('amenager la salle de bains et la cuisine')).toEqual(expect.arrayContaining(['bathroom', 'kitchen'])); // FR
    expect(roomsOf('innrede kjøkkenet og soverommet')).toEqual(expect.arrayContaining(['kitchen', 'bedroom']));        // NO (ø fold)
    expect(roomsOf('innrede kontoret og soverommet')).toEqual(expect.arrayContaining(['home-office', 'bedroom']));     // NO
    expect(roomsOf('quero mobilar o quarto e a cozinha')).toEqual(expect.arrayContaining(['bedroom', 'kitchen']));     // PT
    expect(roomsOf('mobilar a casa de banho e a cozinha')).toEqual(expect.arrayContaining(['bathroom', 'kitchen']));   // PT
    expect(roomsOf('zariadit kuchynu a obyvacku')).toEqual(expect.arrayContaining(['kitchen', 'living-room']));        // SK
    expect(roomsOf('opremiti spalnico in kuhinjo')).toEqual(expect.arrayContaining(['bedroom', 'kitchen']));           // SI
    expect(roomsOf('inreda vardagsrummet och köket')).toEqual(expect.arrayContaining(['living-room', 'kitchen']));     // SE (köket)
  });

  it('detects a whole-apartment / N-room signal', () => {
    expect(detectMultiRoom('devo arredare un trilocale nuovo')).not.toBeNull();          // IT bilocale/trilocale
    expect(detectMultiRoom('3-Zimmer-Wohnung komplett einrichten')).not.toBeNull();      // DE hyphenated N-room
    expect(detectMultiRoom('meine erste Wohnung komplett einrichten')).not.toBeNull();   // DE first flat
  });

  it('does not count a room the user ruled out', () => {
    // Only the kitchen is actually wanted, so this is a single-room request → no whole-apartment nudge.
    expect(detectMultiRoom('uredi kuhinju, ne dnevni boravak')).toBeNull();
  });

  it('adversarial: a "bad feeling" is not a bathroom; a move-in phrase is a whole-apartment', () => {
    // The English adjective "bad" must not conjure a bathroom room and fire the multi-room nudge.
    expect(detectMultiRoom("a hand with my living room; i've a bad feeling the old sofa is too big")).toBeNull();
    // Just-moved-in phrasings across markets trigger the whole-apartment nudge.
    expect(detectMultiRoom('acabo de mudarme y quiero amueblar todo el piso')).not.toBeNull();      // ES
    expect(detectMultiRoom('je viens de demenager et je dois meubler tout l appartement')).not.toBeNull(); // FR
    expect(detectMultiRoom('we zijn net verhuisd en moeten het hele huis inrichten')).not.toBeNull();  // NL
    expect(detectMultiRoom('vi har nettopp flyttet inn og skal moblere hele leiligheten')).not.toBeNull(); // NO
  });

  it('does not read the PT "quarto de banho" (bathroom) as a bedroom', () => {
    // Single room (bathroom) → no multi-room nudge, and never a bedroom.
    expect(roomsOf('mobilar so o quarto de banho')).not.toContain('bedroom');
  });
});
