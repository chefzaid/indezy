import { DEFAULT_BASE, addressOfForm, isWebPage } from './links.js';

const MENU_ID = 'indezy-add-link';

async function base() {
  const { base: saved } = await chrome.storage.sync.get('base');
  return saved || DEFAULT_BASE;
}

/** Opens Indezy's form for the offer at {@code address}, next to the current tab; a page that is not one is ignored. */
async function openInIndezy(address, index) {
  if (!isWebPage(address)) {
    return null;
  }
  return chrome.tabs.create({ url: addressOfForm(await base(), address), index: index === undefined ? undefined : index + 1 });
}

// Exposed for the end-to-end test, which cannot click the toolbar.
globalThis.openInIndezy = openInIndezy;

chrome.runtime.onInstalled.addListener(() => {
  chrome.contextMenus.create({ id: MENU_ID, title: chrome.i18n.getMessage('menuLink'), contexts: ['link'] });
});

chrome.action.onClicked.addListener(tab => openInIndezy(tab.url, tab.index));

chrome.contextMenus.onClicked.addListener((info, tab) => {
  if (info.menuItemId === MENU_ID) {
    openInIndezy(info.linkUrl, tab?.index);
  }
});
