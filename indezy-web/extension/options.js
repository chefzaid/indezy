import { DEFAULT_BASE, normalizeBase } from './links.js';

for (const element of document.querySelectorAll('[data-i18n]')) {
  element.textContent = chrome.i18n.getMessage(element.dataset.i18n);
}
document.documentElement.lang = chrome.i18n.getUILanguage();

const input = document.getElementById('base');
const status = document.getElementById('status');
const { base } = await chrome.storage.sync.get('base');
input.value = base || DEFAULT_BASE;

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
  status.textContent = chrome.i18n.getMessage('optionsSaved');
});
