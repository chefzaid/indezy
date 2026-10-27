import { strict as assert } from 'node:assert';
import { test } from 'node:test';
import { DEFAULT_BASE, addressOfForm, isWebPage, normalizeBase } from '../links.js';

test('keeps an Indezy address over HTTPS, or a local one, without path', () => {
  assert.equal(normalizeBase(' https://indezy.example.com/ '), 'https://indezy.example.com');
  assert.equal(normalizeBase('https://indezy.example.com:8443'), 'https://indezy.example.com:8443');
  assert.equal(normalizeBase('http://localhost:4200'), 'http://localhost:4200');
  assert.equal(normalizeBase('http://127.0.0.1:4200/'), 'http://127.0.0.1:4200');
  for (const refused of ['http://indezy.example.com', 'https://indezy.example.com/app', 'https://user:pw@indezy.example.com',
    'https://indezy.example.com/?a=1', 'https://indezy.example.com/#x', 'javascript:alert(1)', 'not an address', '', null, undefined]) {
    assert.equal(normalizeBase(refused), null, String(refused));
  }
});

test('takes web pages only', () => {
  assert.ok(isWebPage('https://www.welcometothejungle.com/fr/companies/x/jobs/y'));
  assert.ok(isWebPage('http://jobs.example/7'));
  for (const refused of ['chrome://extensions', 'file:///home/me/offer.html', 'about:blank', 'chrome-extension://abc/options.html', '', undefined]) {
    assert.equal(isWebPage(refused), false, String(refused));
  }
});

test('opens the new-opportunity form to look the offer up', () => {
  assert.equal(addressOfForm('https://indezy.example.com/', 'https://jobs.example/offer?id=7&utm=x#apply'),
    'https://indezy.example.com/projects/new?from=link&url=https%3A%2F%2Fjobs.example%2Foffer%3Fid%3D7%26utm%3Dx%23apply');
  assert.equal(addressOfForm('ftp://bad', 'https://jobs.example/7'), `${DEFAULT_BASE}/projects/new?from=link&url=https%3A%2F%2Fjobs.example%2F7`);
});
