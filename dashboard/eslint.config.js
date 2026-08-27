import { defineConfig } from 'eslint/config'
import globals from 'globals'
import pluginJs from '@eslint/js'
import vuePlugin from 'eslint-plugin-vue'
import vueParser from 'vue-eslint-parser'
import tsParser from '@typescript-eslint/parser'
import pluginTs from '@typescript-eslint/eslint-plugin'
import eslintConfigPrettier from 'eslint-config-prettier'

export default defineConfig(
  { ignores: ['dist/', 'node_modules/', '.venv/'] },
  { files: ['**/*.{js,ts,vue}'] },
  { languageOptions: { globals: globals.browser } },

  // Base JS linting
  pluginJs.configs.recommended,

  // Vue linting
  ...vuePlugin.configs['flat/recommended'],
  ...vuePlugin.configs['flat/strongly-recommended'],

  // TypeScript rules (sets tsParser globally — will be overridden for .vue below)
  ...pluginTs.configs['flat/recommended'],

  // TypeScript files
  {
    files: ['**/*.ts'],
    languageOptions: { parser: tsParser },
  },

  // Vue files: vue-eslint-parser with TypeScript inner parser (MUST come after TS rules)
  {
    files: ['**/*.vue'],
    languageOptions: {
      parser: vueParser,
      parserOptions: { parser: tsParser },
    },
    rules: { 'vue/multi-word-component-names': 'off' },
  },

  // Dead code + code quality
  {
    rules: {
      '@typescript-eslint/no-unused-vars': ['warn', { argsIgnorePattern: '^_' }],
      '@typescript-eslint/no-explicit-any': 'warn',
      'no-console': ['warn', { allow: ['warn', 'error'] }],
      'no-debugger': 'error',
      'no-restricted-globals': [
        'error',
        { name: 'alert', message: 'Use the shared notification or inline error UI.' },
      ],
      'no-restricted-syntax': [
        'error',
        {
          selector: "CallExpression[callee.name='fetch']",
          message: 'Use the shared API transport instead of direct fetch().',
        },
      ],
    },
  },
  // Must be last — disables formatting rules that conflict with Prettier
  eslintConfigPrettier,
  {
    files: ['src/api/shared.ts'],
    rules: { 'no-restricted-syntax': 'off' },
  },
  {
    files: ['tests/e2e/**/*.{js,ts}'],
    rules: { 'no-restricted-syntax': 'off' },
  },
)
