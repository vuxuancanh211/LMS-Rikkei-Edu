import js from '@eslint/js';
import boundaries from 'eslint-plugin-boundaries';
import unusedImports from 'eslint-plugin-unused-imports';
import tseslint from 'typescript-eslint';

export default tseslint.config(
  { ignores: ['dist/**', 'node_modules/**'] },
  js.configs.recommended,
  ...tseslint.configs.recommended,
  {
    files: ['src/**/*.{ts,tsx}'],
    plugins: {
      boundaries,
      'unused-imports': unusedImports,
    },
    languageOptions: {
      globals: {
        React: 'readonly',
        window: 'readonly',
        document: 'readonly',
        HTMLElement: 'readonly',
        HTMLInputElement: 'readonly',
        setTimeout: 'readonly',
        clearTimeout: 'readonly',
      },
    },
    rules: {
      '@typescript-eslint/no-explicit-any': 'off',
      '@typescript-eslint/ban-ts-comment': 'off',
      '@typescript-eslint/no-unused-expressions': 'off',
      '@typescript-eslint/no-unused-vars': 'off',
      'no-undef': 'off',
      'no-useless-assignment': 'off',
      'prefer-const': 'off',
      'unused-imports/no-unused-imports': 'warn',
    },
  },
);
