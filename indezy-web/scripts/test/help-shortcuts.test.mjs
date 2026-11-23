import { strict as assert } from 'node:assert';
import { readFileSync } from 'node:fs';
import { test } from 'node:test';

// The keyboard shortcuts article lists every shortcut the app answers, in both languages, so it never drifts from them.
const read = path => readFileSync(new URL(`../../${path}`, import.meta.url), 'utf8');
const article = language => read(`src/assets/help/${language}/shortcuts.md`);

test('lists every Alt + Shift shortcut of the app', () => {
  const keys = [...read('src/app/shared/shortcuts/keyboard-shortcuts.service.ts').matchAll(/case 'Key([A-Z])'/g)].map(match => match[1]);
  assert.ok(keys.length >= 5, 'the service names its shortcuts');
  for (const key of keys) {
    assert.ok(article('en').includes(`\`Alt + Shift + ${key}\``), `Alt + Shift + ${key} in the English article`);
    assert.ok(article('fr').includes(`\`Alt + Maj + ${key}\``), `Alt + Maj + ${key} in the French article`);
  }
});

test('lists every key of the offer feed', () => {
  const directive = read('src/app/components/offers/offer-feed/offer-list-keys.directive.ts');
  const table = /const ACTIONS[^=]*=\s*\{([^}]*)\}/.exec(directive)[1];
  const actions = [...table.matchAll(/(\w): '/g)].map(match => match[1]);
  const moves = [...directive.matchAll(/key === '([a-z])'/g)].map(match => match[1]);
  for (const key of [...new Set([...moves, ...actions])]) {
    for (const language of ['en', 'fr']) {
      assert.ok(article(language).includes(`\`${key.toUpperCase()}\``), `${key.toUpperCase()} in the ${language} article`);
    }
  }
});
