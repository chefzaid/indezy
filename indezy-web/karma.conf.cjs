const path = require('node:path');

module.exports = function configureKarma(config) {
  config.set({
    basePath: '',
    frameworks: ['jasmine'],
    plugins: [
      require('karma-jasmine'),
      require('karma-chrome-launcher'),
      require('karma-coverage'),
      require('karma-junit-reporter'),
    ],
    reporters: ['progress', 'junit', 'coverage'],
    junitReporter: {
      outputDir: 'test-results',
      outputFile: 'karma-junit.xml',
      useBrowserName: false,
    },
    coverageReporter: {
      dir: path.join(__dirname, 'coverage'),
      subdir: '.',
      reporters: [
        { type: 'text-summary' },
        { type: 'html' },
        { type: 'lcovonly' },
        { type: 'cobertura', file: 'cobertura-coverage.xml' },
      ],
    },
    customLaunchers: {
      ChromeHeadlessCI: {
        base: 'ChromeHeadless',
        flags: ['--no-sandbox', '--disable-dev-shm-usage'],
      },
    },
    // Chrome gets 30 seconds to exit after the run instead of 2. Past the limit Karma 6.4's SIGKILL timer can find the
    // browser already gone, throw, and leave Karma waiting forever: in CI the coverage reports outlast 2 seconds.
    processKillTimeout: 30000,
    restartOnFileChange: true,
  });
};
