module.exports = {
  extends: 'eslint:recommended',
  parserOptions: { ecmaVersion: 6 },
  env: {
    es6: true,
    browser: true
  },
  parserOptions: { sourceType: 'module' },
  rules: {
    'no-console': 'off'
  }
}
