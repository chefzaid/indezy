/** At most this many new facts are told per text. */
const FACTS_MAX = 8;

/**
 * The numbers, dates and names in {@code text} that {@code source} never states (folded: accents and case aside):
 * figures, acronyms ("SI", "AWS"), names with a digit or a sign ("S3", "C#"), mixed case ("GitLab"), and capitalized
 * words that do not open a sentence ("Kafka"). What the AI may have introduced, or the freelancer added, to check.
 */
export function newFacts(text: string | null | undefined, source: string): string[] {
  const known = new Set(tokens(source).map(token => fold(token.word)));
  const knownNumbers = new Set(tokens(source).map(token => digits(token.word)).filter(Boolean));
  const facts: string[] = [];
  for (const token of tokens(text ?? '')) {
    const word = token.word;
    const number = digits(word);
    const fact = number ? !knownNumbers.has(number) && !known.has(fold(word)) :
      looksLikeAName(word, token.opensSentence) && !known.has(fold(word));
    if (fact && !facts.includes(word)) {
      facts.push(word);
    }
    if (facts.length === FACTS_MAX) {
      break;
    }
  }
  return facts;
}

interface Token {
  word: string;
  opensSentence: boolean;
}

function tokens(text: string): Token[] {
  const found: Token[] = [];
  let opensSentence = true;
  for (const raw of text.split(/\s+/)) {
    const word = raw.replace(/^[("«“'‘]+/, '').replace(/[)»”'’.,;:!?…]+$/, '');
    if (word) {
      found.push({ word, opensSentence });
    }
    opensSentence = /[.!?…:]["»”)]*$/.test(raw) || (opensSentence && !word);
  }
  return found;
}

function looksLikeAName(word: string, opensSentence: boolean): boolean {
  const letters = word.replace(/[^\p{L}]/gu, '');
  const capitals = (word.match(/\p{Lu}/gu) ?? []).length;
  return /[+#]/.test(word) || capitals >= 2 || (capitals === 1 && /^\p{Lu}/u.test(word) && !opensSentence && letters.length > 1);
}

/** A number's digits, with its unit sign when it has one ("40%", "2026"); empty for a word without a digit. */
function digits(word: string): string {
  return /\d/.test(word) && !/\p{L}{2,}/u.test(word) ? word.replace(/[\s  .,]/g, '').toLowerCase() : '';
}

function fold(word: string): string {
  return word.normalize('NFD').replace(/\p{M}+/gu, '').toLowerCase();
}
