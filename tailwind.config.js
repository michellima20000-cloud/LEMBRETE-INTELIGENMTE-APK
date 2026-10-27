/** @type {import('tailwindcss').Config} */
export default {
  content: ['./index.html', './src/**/*.{js,ts,jsx,tsx}'],
  theme: {
    extend: {
      colors: {
        brandDark: '#090D1C',
        brandCard: '#121933',
        brandBorder: '#26335C',
      },
    },
  },
  plugins: [],
};
