import en from '../../../../assets/i18n/en.json';
import { NOTE_TEMPLATES } from './note-templates';

/** Resolves a dot-separated key against the translation object, or undefined. */
function resolve(tree: unknown, key: string): unknown {
  return key.split('.').reduce<unknown>((node, part) => {
    if (node && typeof node === 'object') {
      return (node as Record<string, unknown>)[part];
    }
    return undefined;
  }, tree);
}

describe('NOTE_TEMPLATES', () => {
  it('has unique template ids', () => {
    const ids = NOTE_TEMPLATES.map(t => t.id);
    expect(new Set(ids).size).toBe(ids.length);
  });

  it('references label and body keys that exist and are non-empty', () => {
    for (const template of NOTE_TEMPLATES) {
      const label = resolve(en, template.labelKey);
      const body = resolve(en, template.bodyKey);
      expect(typeof label)
        .withContext(`missing label for ${template.id} (${template.labelKey})`)
        .toBe('string');
      expect((label as string).length).toBeGreaterThan(0);
      expect(typeof body)
        .withContext(`missing body for ${template.id} (${template.bodyKey})`)
        .toBe('string');
      expect((body as string).length).toBeGreaterThan(0);
    }
  });
});
