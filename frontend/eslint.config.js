import js from '@eslint/js'

export default [
  js.configs.recommended,
  {
    files: ['src/**/*.{js,mjs}'],
    languageOptions: {
      ecmaVersion: 2022,
      sourceType: 'module',
      globals: {
        window: 'readonly',
        document: 'readonly',
        sessionStorage: 'readonly',
        localStorage: 'readonly',
        fetch: 'readonly',
        console: 'readonly',
        URLSearchParams: 'readonly',
        FileReader: 'readonly',
        Event: 'readonly',
        setTimeout: 'readonly',
        clearTimeout: 'readonly',
        setInterval: 'readonly',
        clearInterval: 'readonly',
        location: 'readonly',
        history: 'readonly',
        process: 'readonly',
        globalThis: 'readonly'
      }
    },
    rules: {
      'no-unused-vars': ['warn', { argsIgnorePattern: '^_', varsIgnorePattern: '^_' }],
      'no-console': ['warn', { allow: ['warn', 'error'] }],
      'no-debugger': 'error',
      'prefer-const': 'warn',
      eqeqeq: ['warn', 'smart']
    }
  },
  {
    ignores: ['node_modules/**', 'dist/**', '../src/main/resources/static/**', 'eslint.config.js']
  }
]
