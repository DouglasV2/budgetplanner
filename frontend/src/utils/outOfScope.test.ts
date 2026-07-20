// Sprint 10.189 multilingual synonym audit (2026-07-18): the honest "we don't sell {X}" banner is deterministic.
// These cases are native out-of-scope prompts that used to slip past the detector (so the user got a furniture plan
// for a washing machine / tiles / a balcony), plus the false-positive guards for TV furniture wrongly read as a TV.
import { describe, expect, it } from 'vitest';
import { detectOutOfScope } from './outOfScope';

describe('detectOutOfScope — multilingual coverage', () => {
  it('flags appliances in every market language', () => {
    expect(detectOutOfScope('ich brauche eine spülmaschine für die küche')).toBe('appliances'); // DE dishwasher (colloquial)
    expect(detectOutOfScope('necesito un horno para la cocina')).toBe('appliances');            // ES oven
    expect(detectOutOfScope('vorrei un climatizzatore per il salotto')).toBe('appliances');     // IT air conditioner
    expect(detectOutOfScope('ik wil een magnetron voor in de keuken')).toBe('appliances');      // NL microwave
    expect(detectOutOfScope('jeg trenger en oppvaskmaskin')).toBe('appliances');                // NO dishwasher
    expect(detectOutOfScope('ich suche einen backofen')).toBe('appliances');                    // DE oven
  });

  it('flags tiles (ES/PT) and inflected HR materials', () => {
    expect(detectOutOfScope('quiero azulejos para el baño')).toBe('materials');       // ES wall tiles
    expect(detectOutOfScope('keramičke pločice u kupaonici')).toBe('materials');      // HR inflected tiles
    expect(detectOutOfScope('treba mi izolacija za zid')).toBe('materials');          // HR insulation
  });

  it('flags inflected/Scandinavian balcony as outdoor', () => {
    expect(detectOutOfScope('møbler til balkong')).toBe('outdoor');       // NO/SE balkong
    expect(detectOutOfScope('trebam stol za balkon u stanu')).toBe('outdoor'); // HR balkon (inflected)
  });

  it('does NOT flag TV furniture or a console table as electronics', () => {
    expect(detectOutOfScope('ich suche ein tv-regal fürs wohnzimmer')).toBeNull(); // DE TV shelf
    expect(detectOutOfScope('jeg trenger et tv-bord til stua')).toBeNull();        // NO TV table
    expect(detectOutOfScope('jag vill ha en tv-bänk')).toBeNull();                 // SE TV bench
    expect(detectOutOfScope('i need a console table for the hallway')).toBeNull(); // EN console table
  });

  it('does not flag a mention the user ruled out', () => {
    expect(detectOutOfScope('ne trebam perilicu, samo namjestaj')).toBeNull();          // HR
    expect(detectOutOfScope('i do not want a washing machine, just furniture')).toBeNull(); // EN
    expect(detectOutOfScope('keine waschmaschine, nur moebel')).toBeNull();             // DE
  });

  it('still flags a real television / games console', () => {
    expect(detectOutOfScope('trebam televizor 55 inca')).toBe('electronics');
    expect(detectOutOfScope('i want a playstation for the living room')).toBe('electronics');
  });
});
