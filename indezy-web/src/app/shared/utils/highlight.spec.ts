import { highlight } from './highlight';

describe('highlight', () => {
  const marked = (text: string, query: string): string =>
    highlight(text, query).map(part => part.match ? `[${part.text}]` : part.text).join('');

  it('marks every word searched for, accents and case aside', () => {
    expect(marked('Appel avec Hélène, puis HELENE', 'helene')).toBe('Appel avec [Hélène], puis [HELENE]');
    expect(marked('Développeur Java senior', 'java dev')).toBe('[Dév]eloppeur [Java] senior');
    expect(marked('Kafka', 'nothing')).toBe('Kafka');
  });

  it('keeps the text whole when nothing is searched or there is no text', () => {
    expect(highlight('Kafka', '  ')).toEqual([{ text: 'Kafka', match: false }]);
    expect(highlight(undefined, 'kafka')).toEqual([]);
    expect(highlight('', 'kafka')).toEqual([]);
  });

  it('marks letters written with several code units', () => {
    expect(marked('Équipe 🚀 Kafka', 'kafka')).toBe('Équipe 🚀 [Kafka]');
  });
});
