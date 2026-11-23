import { DEFAULT_BASE, addressOfForm, isWebPage } from './links.js';
import { fillFields, valuesOf } from './fill.js';

const MENU_ID = 'indezy-add-link';
const FILL_ID = 'indezy-fill-form';

async function base() {
  const { base: saved } = await chrome.storage.sync.get('base');
  return saved || DEFAULT_BASE;
}

/** Opens Indezy's form for the offer at {@code address}, next to the current tab; a page that is not one is ignored. */
async function openInIndezy(address, index, active = true) {
  if (!isWebPage(address)) {
    return null;
  }
  return chrome.tabs.create({ url: addressOfForm(await base(), address), index: index === undefined ? undefined : index + 1, active });
}

/** What the page says once filled, shown in the page itself: there is no other place an extension's worker can write. */
function tell(message) {
  document.querySelector('[data-indezy-note]')?.remove();
  const note = document.createElement('div');
  note.setAttribute('data-indezy-note', '');
  note.setAttribute('role', 'status');
  note.textContent = message;
  note.style.cssText = 'position:fixed;z-index:2147483647;bottom:16px;right:16px;max-width:360px;padding:12px 16px;border-radius:8px;' +
    'background:#1f2937;color:#fff;font:14px/1.4 system-ui,sans-serif;box-shadow:0 4px 12px rgba(0,0,0,.3)';
  document.body.appendChild(note);
  setTimeout(() => note.remove(), 8000);
}

/**
 * Fills the application form of the tab with the Indezy profile read with the token of the options, then opens Indezy's
 * form for the page so the application is kept in the pipeline; the form is never sent. The number of fields filled,
 * or null when nothing could be read.
 */
async function fillForm(tab) {
  const { token } = await chrome.storage.local.get('token');
  const say = message => chrome.scripting.executeScript({ target: { tabId: tab.id }, func: tell, args: [message] });
  if (!token) {
    await say(chrome.i18n.getMessage('fillNoToken'));
    return null;
  }
  let profile;
  try {
    const response = await fetch(`${await base()}/api/application-profile`, { headers: { Authorization: `Bearer ${token}`, Accept: 'application/json' } });
    if (!response.ok) {
      throw new Error(String(response.status));
    }
    profile = await response.json();
  } catch {
    await say(chrome.i18n.getMessage('fillFailed'));
    return null;
  }
  const [result] = await chrome.scripting.executeScript({ target: { tabId: tab.id }, func: fillFields, args: [valuesOf(profile)] });
  const filled = result?.result ?? 0;
  await say(filled ? chrome.i18n.getMessage('fillDone', [String(filled)]) : chrome.i18n.getMessage('fillNothing'));
  if (filled) {
    await openInIndezy(tab.url, tab.index, false);
  }
  return filled;
}

// Exposed for the end-to-end test, which cannot click the toolbar or a menu.
globalThis.openInIndezy = openInIndezy;
globalThis.fillForm = fillForm;

chrome.runtime.onInstalled.addListener(() => {
  chrome.contextMenus.create({ id: MENU_ID, title: chrome.i18n.getMessage('menuLink'), contexts: ['link'] });
  chrome.contextMenus.create({ id: FILL_ID, title: chrome.i18n.getMessage('menuFill'), contexts: ['page', 'editable'] });
});

chrome.action.onClicked.addListener(tab => openInIndezy(tab.url, tab.index));

chrome.contextMenus.onClicked.addListener((info, tab) => {
  if (info.menuItemId === MENU_ID) {
    openInIndezy(info.linkUrl, tab?.index);
  } else if (info.menuItemId === FILL_ID && tab) {
    fillForm(tab);
  }
});
