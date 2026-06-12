const angularTemplate = require('@angular-eslint/eslint-plugin-template');
const angularTemplateParser = require('@angular-eslint/template-parser');
const tsPlugin = require('@typescript-eslint/eslint-plugin');
const tsParser = require('@typescript-eslint/parser');

const sharedTypeScriptRules = {
  '@typescript-eslint/no-unused-vars': [
    'warn',
    {
      argsIgnorePattern: '^_',
      varsIgnorePattern: '^_'
    }
  ],
  '@typescript-eslint/explicit-function-return-type': 'warn',
  '@typescript-eslint/no-explicit-any': 'warn',
  '@typescript-eslint/no-empty-function': 'warn',
  'no-console': [
    'warn',
    {
      allow: ['warn', 'error']
    }
  ],
  'no-debugger': 'warn',
  'no-duplicate-imports': 'warn',
  'no-unused-expressions': 'error',
  'prefer-const': 'warn',
  eqeqeq: ['warn', 'always'],
  curly: 'warn',
  'no-unused-vars': 'off',
  'no-undef': 'off'
};

module.exports = [
  {
    ignores: [
      'dist/',
      'coverage/',
      '.angular/',
      'node_modules/',
      '../indezy-server/target/',
      '**/target/',
      '**/*.generated.ts',
      '**/*.generated.js',
      'test-results/',
      '*.log',
      '*.tmp',
      '*.temp',
      '.cache/',
      'projects/**/*'
    ]
  },
  {
    files: ['**/*.ts'],
    languageOptions: {
      parser: tsParser,
      parserOptions: {
        project: ['./tsconfig.json', './tsconfig.spec.json'],
        tsconfigRootDir: __dirname,
        sourceType: 'module'
      },
      ecmaVersion: 2022,
      sourceType: 'module'
    },
    plugins: {
      '@typescript-eslint': tsPlugin
    },
    rules: sharedTypeScriptRules
  },
  {
    // Tests rely on partial mocks and fixtures cast to framework types,
    // and spec callbacks (it/beforeEach) don't benefit from explicit return types.
    files: ['**/*.spec.ts'],
    rules: {
      '@typescript-eslint/no-explicit-any': 'off',
      '@typescript-eslint/explicit-function-return-type': 'off'
    }
  },
  {
    files: ['**/*.html'],
    languageOptions: {
      parser: angularTemplateParser
    },
    plugins: {
      '@angular-eslint/template': angularTemplate
    },
    rules: {}
  }
];
