// Pure helpers of the extension, shared by its worker and options page, and tested with Node.

/** Where Indezy is unless the options say otherwise. */
export const DEFAULT_BASE = 'https://indezy.swirlit.dev';

/**
 * The Indezy address typed in the options, without a trailing slash, or null when it is not one: HTTPS only, except a
 * local server (http://localhost or 127.0.0.1) for development; no path, query, user or fragment.
 */
export function normalizeBase(text) {
  let url;
  try {
    url = new URL(String(text ?? '').trim());
  } catch {
    return null;
  }
  const local = url.hostname === 'localhost' || url.hostname === '127.0.0.1';
  if (!(url.protocol === 'https:' || (url.protocol === 'http:' && local)) || url.username || url.password || url.search || url.hash ||
      (url.pathname !== '/' && url.pathname !== '')) {
    return null;
  }
  return url.origin;
}

/** Whether a page's address can be an offer: a web page, not the browser's own pages or a local file. */
export function isWebPage(address) {
  try {
    const url = new URL(address);
    return url.protocol === 'https:' || url.protocol === 'http:';
  } catch {
    return false;
  }
}

/** Indezy's new-opportunity form, opened to look the offer at {@code address} up. */
export function addressOfForm(base, address) {
  return `${normalizeBase(base) ?? DEFAULT_BASE}/projects/new?from=link&url=${encodeURIComponent(address)}`;
}
