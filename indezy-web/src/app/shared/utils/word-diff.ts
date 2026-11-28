/** A run of words the same in both texts, only in the old one, or only in the new one. */
export interface DiffSegment {
  kind: 'same' | 'removed' | 'added';
  text: string;
}

/** Past this many words on either side, the texts are shown as replaced whole rather than compared word by word. */
const WORDS_MAX = 600;

/**
 * The words of {@code before} and {@code after} compared (the longest common subsequence of words, spaces kept with the
 * word they follow), as runs to show the removed words struck through and the added ones marked.
 */
export function wordDiff(before: string | null | undefined, after: string | null | undefined): DiffSegment[] {
  const old = words(before ?? '');
  const next = words(after ?? '');
  if (old.length > WORDS_MAX || next.length > WORDS_MAX) {
    return merge([...old.map(text => ({ kind: 'removed' as const, text })), ...next.map(text => ({ kind: 'added' as const, text }))]);
  }
  // lengths[i][j]: the longest common run of old[i..] and next[j..].
  const lengths: number[][] = Array.from({ length: old.length + 1 }, () => new Array<number>(next.length + 1).fill(0));
  for (let i = old.length - 1; i >= 0; i--) {
    for (let j = next.length - 1; j >= 0; j--) {
      lengths[i][j] = same(old[i], next[j]) ? lengths[i + 1][j + 1] + 1 : Math.max(lengths[i + 1][j], lengths[i][j + 1]);
    }
  }
  const segments: DiffSegment[] = [];
  let i = 0;
  let j = 0;
  while (i < old.length || j < next.length) {
    if (i < old.length && j < next.length && same(old[i], next[j])) {
      segments.push({ kind: 'same', text: next[j] });
      i++;
      j++;
    } else if (i < old.length && (j === next.length || lengths[i + 1][j] >= lengths[i][j + 1])) {
      // On a tie, the words removed come before the ones that replace them.
      segments.push({ kind: 'removed', text: old[i++] });
    } else {
      segments.push({ kind: 'added', text: next[j++] });
    }
  }
  return merge(segments);
}

/** Whether a text changed beyond its spaces. */
export function changed(before: string | null | undefined, after: string | null | undefined): boolean {
  return words(before ?? '').map(word => word.trim()).join(' ') !== words(after ?? '').map(word => word.trim()).join(' ');
}

function words(text: string): string[] {
  return text.match(/\S+\s*/g) ?? [];
}

function same(a: string, b: string): boolean {
  return a.trim() === b.trim();
}

function merge(segments: DiffSegment[]): DiffSegment[] {
  const merged: DiffSegment[] = [];
  for (const segment of segments) {
    const last = merged[merged.length - 1];
    if (last?.kind === segment.kind) {
      last.text += segment.text;
    } else {
      merged.push({ ...segment });
    }
  }
  return merged;
}
