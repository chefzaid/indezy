import base from './playwright.config';

// The presentation page's screenshots, taken on request from a demo workspace (scripts/screenshots).
export default {
  ...base,
  testDir: './scripts/screenshots',
  reporter: 'list',
  retries: 0
};
