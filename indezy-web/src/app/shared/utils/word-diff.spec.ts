import { changed, wordDiff } from './word-diff';

describe('wordDiff', () => {
  it('keeps the common words and marks the ones removed and added', () => {
    expect(wordDiff('Migration vers Kafka.', 'Migration du SI vers Kafka.')).toEqual([
      { kind: 'same', text: 'Migration ' }, { kind: 'added', text: 'du SI ' }, { kind: 'same', text: 'vers Kafka.' }]);
    expect(wordDiff('Dix ans de Java.', 'Dix ans de Kotlin.')).toEqual([
      { kind: 'same', text: 'Dix ans de ' }, { kind: 'removed', text: 'Java.' }, { kind: 'added', text: 'Kotlin.' }]);
  });

  it('reads a missing text as empty and a long one as replaced whole', () => {
    expect(wordDiff(null, 'Nouveau.')).toEqual([{ kind: 'added', text: 'Nouveau.' }]);
    expect(wordDiff('Ancien.', '')).toEqual([{ kind: 'removed', text: 'Ancien.' }]);
    const long = 'mot '.repeat(700);
    expect(wordDiff(long, long + 'fin').map(segment => segment.kind)).toEqual(['removed', 'added']);
  });

  it('tells a change from a change of spaces only', () => {
    expect(changed('Dix  ans de Java.', ' Dix ans de Java. ')).toBeFalse();
    expect(changed('Dix ans de Java.', 'Dix ans de Kotlin.')).toBeTrue();
  });
});
