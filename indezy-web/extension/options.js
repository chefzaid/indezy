import { DEFAULT_BASE, normalizeBase } from './links.js';

for (const element of document.querySelectorAll('[data-i18n]')) {
  element.textContent = chrome.i18n.getMessage(element.dataset.i18n);
}
document.documentElement.lang = chrome.i18n.getUILanguage();

const input = document.getElementById('base');
const status = document.getElementById('status');
const token = document.getElementById('token');
const { base } = await chrome.storage.sync.get('base');
input.value = base || DEFAULT_BASE;
// The token stays on this computer: never synced with the browser's account.
token.value = (await chrome.storage.local.get('token')).token || '';

document.getElementById('form').addEventListener('submit', async event => {
  event.preventDefault();
  const normalized = normalizeBase(input.value);
  status.classList.toggle('error', !normalized);
  if (!normalized) {
    status.textContent = chrome.i18n.getMessage('optionsInvalid');
    input.setAttribute('aria-invalid', 'true');
    return;
  }
  input.removeAttribute('aria-invalid');
  input.value = normalized;
  await chrome.storage.sync.set({ base: normalized });
  const typed = token.value.trim();
  if (typed) {
    await chrome.storage.local.set({ token: typed });
  } else {
    await chrome.storage.local.remove('token');
  }
  // Another Indezy than the usual one is reached only once allowed (a local one is allowed from the start).
  if (!normalized.startsWith('http://localhost') && !normalized.startsWith('http://127.0.0.1') && normalized !== DEFAULT_BASE && typed) {
    await chrome.permissions.request({ origins: [`${normalized}/*`] }).catch(() => false);
  }
  status.textContent = chrome.i18n.getMessage('optionsSaved');
});
