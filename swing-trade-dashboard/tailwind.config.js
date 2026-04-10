/** @type {import('tailwindcss').Config} */
export default {
  content: [
    './index.html',
    './src/**/*.{vue,js,ts,jsx,tsx}',
  ],
  theme: {
    extend: {
      colors: {
        gray: {
          100: '#f1f5f9',
          200: '#e2e8f0',
          300: '#cbd5e1',
          400: '#94a3b8',
          500: '#64748b',
          600: '#475569',
          700: '#334155',
          800: '#1e293b',
          900: '#0f172a',
        },
        success: {
          50: '#f0fdf4',
          100: '#dcfce7',
          500: '#22c55e',
          600: '#16a34a',
        },
        warning: {
          50: '#fffbeb',
          100: '#fef3c7',
          500: '#f59e0b',
          600: '#d97706',
        },
        error: {
          50: '#fef2f2',
          100: '#fee2e2',
          500: '#ef4444',
          600: '#dc2626',
        },
      },
      boxShadow: {
        'theme-xs': '0px 1px 2px 0px rgba(0, 0, 0, 0.05)',
        'theme-sm': '0px 2px 4px 0px rgba(0, 0, 0, 0.05)',
        'theme-lg': '0px 8px 16px 0px rgba(0, 0, 0, 0.1)',
      },
      spacing: {
        'theme-xs': '0.5rem',
        'theme-sm': '1rem',
        'theme-md': '1.5rem',
        'theme-lg': '2rem',
      },
      fontSize: {
        'theme-xs': '0.75rem',
        'theme-sm': '0.875rem',
        'theme-base': '1rem',
        'theme-lg': '1.125rem',
        'theme-xl': '1.25rem',
        'title-sm': '1.5rem',
        'title-md': '1.875rem',
        'title-lg': '2.25rem',
      },
    },
  },
  plugins: [],
}
