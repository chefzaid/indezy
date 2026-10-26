/** A piece of text, and whether it is one of the words searched for. */
export interface TextPart {
  text: string;
  match: boolean;
}

const fold = (text: string): string => text.normalize('NFD').replace(/\p{M}+/gu, '').toLowerCase();

/**
 * {@code text} cut where the words of {@code query} appear, accents and case aside, so they can be highlighted
 * ("Hélène" in "Appel avec Hélène" for "helene").
 */
export function highlight(text: string | null | undefined, query: string): TextPart[] {
  if (!text) {
    return [];
  }
  // Fold letter by letter, keeping where each folded character comes from.
  let folded = '';
  const origin: number[] = [];
  Array.from(text).reduce((at, char) => {
    const part = fold(char);
    folded += part;
    origin.push(...Array<number>(part.length).fill(at));
    return at + char.length;
  }, 0);
  const marked = new Array<boolean>(text.length).fill(false);
  for (const word of new Set(fold(query).split(/\s+/).filter(Boolean))) {
    for (let at = folded.indexOf(word); at >= 0; at = folded.indexOf(word, at + word.length)) {
      const end = at + word.length < origin.length ? origin[at + word.length] : text.length;
      marked.fill(true, origin[at], end);
    }
  }
  const parts: TextPart[] = [];
  for (let i = 0; i < text.length; i++) {
    const last = parts.at(-1);
    if (last && last.match === marked[i]) {
      last.text += text[i];
    } else {
      parts.push({ text: text[i], match: marked[i] });
    }
  }
  return parts;
}
