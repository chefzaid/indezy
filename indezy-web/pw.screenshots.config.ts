import local from './pw.local.config';

// The presentation page's screenshots, taken on request from a demo workspace (scripts/screenshots).
export default {
  ...local,
  testDir: './scripts/screenshots',
  reporter: 'list',
  retries: 0
};
