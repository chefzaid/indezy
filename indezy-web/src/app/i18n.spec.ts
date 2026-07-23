import en from '../assets/i18n/en.json';
import fr from '../assets/i18n/fr.json';

type TranslationTree = { [key: string]: string | TranslationTree };

/**
 * Flattens a nested translation object into dot-notation keys,
 * e.g. { a: { b: 'x' } } -> { 'a.b': 'x' }.
 */
function flatten(tree: TranslationTree, prefix = ''): Record<string, string> {
  return Object.entries(tree).reduce<Record<string, string>>((acc, [key, value]) => {
    const path = prefix ? `${prefix}.${key}` : key;
    if (value !== null && typeof value === 'object') {
      Object.assign(acc, flatten(value, path));
    } else {
      acc[path] = value as string;
    }
    return acc;
  }, {});
}

describe('i18n translation coverage', () => {
  const enFlat = flatten(en as TranslationTree);
  const frFlat = flatten(fr as TranslationTree);
  const enKeys = new Set(Object.keys(enFlat));
  const frKeys = new Set(Object.keys(frFlat));

  it('has the same set of keys in English and French (no missing translations)', () => {
    const missingInFr = [...enKeys].filter(key => !frKeys.has(key));
    const missingInEn = [...frKeys].filter(key => !enKeys.has(key));

    expect(missingInFr)
      .withContext(`Keys present in en.json but missing from fr.json: ${missingInFr.join(', ')}`)
      .toEqual([]);
    expect(missingInEn)
      .withContext(`Keys present in fr.json but missing from en.json: ${missingInEn.join(', ')}`)
      .toEqual([]);
  });

  it('has no empty translation values', () => {
    const emptyEn = Object.entries(enFlat).filter(([, value]) => value.trim() === '').map(([key]) => key);
    const emptyFr = Object.entries(frFlat).filter(([, value]) => value.trim() === '').map(([key]) => key);

    expect(emptyEn).withContext(`Empty values in en.json: ${emptyEn.join(', ')}`).toEqual([]);
    expect(emptyFr).withContext(`Empty values in fr.json: ${emptyFr.join(', ')}`).toEqual([]);
  });
});
