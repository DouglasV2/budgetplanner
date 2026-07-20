// Sprint 10.190: the frontend negation scope must behave exactly like NegationScope.java, so the banner and the
// Move-In nudge stop firing on things the user ruled out.
import { describe, expect, it } from 'vitest';
import { affirmativeHit, negatedRanges, normalizeForMatch } from './negationScope';

const negated = (sentence: string, needle: string) => {
  const text = normalizeForMatch(sentence);
  return !affirmativeHit(new RegExp(normalizeForMatch(needle)), text, negatedRanges(text));
};

describe('negationScope', () => {
  it('negates what follows a cue, across market languages', () => {
    expect(negated('ne trebam perilicu', 'perilicu')).toBe(true);
    expect(negated('i do not want a washing machine', 'washing machine')).toBe(true);
    expect(negated('keine waschmaschine', 'waschmaschine')).toBe(true);
    expect(negated('ikke noe kjøkken', 'kjokken')).toBe(true);
  });

  it('leaves plain affirmative text alone', () => {
    expect(negated('trebam perilicu posuda', 'perilicu')).toBe(false);
    expect(negated('mobler til balkong', 'balkong')).toBe(false);
  });

  it('stops at a clause break or a contrast word', () => {
    expect(negated('ne trebam perilicu, trebam kauc', 'kauc')).toBe(false);
    expect(negated('ne tamno nego svijetlo', 'svijetlo')).toBe(false);
  });

  it('keeps running across a coordinating "and"', () => {
    expect(negated('ne zelim tamno i crno', 'crno')).toBe(true);
  });

  it('cancels on double negation', () => {
    expect(negated('nicht ohne balkon', 'balkon')).toBe(false);
  });

  it('folds the Nordic ligatures NFD leaves intact', () => {
    expect(normalizeForMatch('kjøkken')).toBe('kjokken');
    expect(normalizeForMatch('værelse')).toBe('vaerelse');
    expect(normalizeForMatch('KÖKET')).toBe('koket');
  });
});
