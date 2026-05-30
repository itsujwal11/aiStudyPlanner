module.exports = {
  content: [
    "./index.html",
    "./src/**/*.{js,jsx}",
  ],
  theme: {
    extend: {
      fontFamily: {
        sans: ['Hanken Grotesk', 'system-ui', 'sans-serif'],
      },
      colors: {
        surface: {
          DEFAULT: '#f7f9fb',
          dim: '#d8dadc',
          bright: '#f7f9fb',
          'container-lowest': '#ffffff',
          'container-low': '#f2f4f6',
          container: '#eceef0',
          'container-high': '#e6e8ea',
          'container-highest': '#e0e3e5',
        },
        'on-surface': '#191c1e',
        'on-surface-variant': '#414755',
        'inverse-surface': '#2d3133',
        'inverse-on-surface': '#eff1f3',
        outline: '#717786',
        'outline-variant': '#c1c6d7',
        primary: {
          DEFAULT: '#0058bc',
          50: '#eef4ff',
          100: '#d8e2ff',
          200: '#adc6ff',
          300: '#7ba8ff',
          400: '#4785ff',
          500: '#1a5dff',
          600: '#0058bc',
          700: '#004493',
          800: '#002d66',
          900: '#001a41',
        },
        'on-primary': '#ffffff',
        'primary-container': '#0070eb',
        'on-primary-container': '#fefcff',
        secondary: {
          DEFAULT: '#006a66',
          50: '#eafff8',
          100: '#bffef0',
          200: '#65f8f0',
          300: '#3fdbd4',
          400: '#00a39c',
          500: '#008580',
          600: '#006a66',
          700: '#00504d',
          800: '#003735',
          900: '#00201e',
        },
        'on-secondary': '#ffffff',
        'secondary-container': '#61f5ed',
        'on-secondary-container': '#006f6a',
        tertiary: {
          DEFAULT: '#4648d4',
          light: '#c0c1ff',
        },
        error: {
          DEFAULT: '#ba1a1a',
          container: '#ffdad6',
          'on-container': '#93000a',
        },
      },
      borderRadius: {
        sm: '0.25rem',
        md: '0.75rem',
        lg: '1rem',
        xl: '1.5rem',
      },
      maxWidth: {
        container: '1440px',
      },
      spacing: {
        sidebar: '280px',
        gutter: '24px',
      },
      backdropBlur: {
        glass: '24px',
        'glass-lg': '40px',
      },
      boxShadow: {
        glass: '0 8px 32px rgba(0, 0, 0, 0.04)',
        'glass-lg': '0 16px 48px rgba(0, 0, 0, 0.05)',
        'glass-sm': '0 2px 8px rgba(0, 0, 0, 0.03)',
      },
      keyframes: {
        'fade-in': {
          '0%': { opacity: '0', transform: 'translateY(8px)' },
          '100%': { opacity: '1', transform: 'translateY(0)' },
        },
        'slide-up': {
          '0%': { opacity: '0', transform: 'translateY(16px)' },
          '100%': { opacity: '1', transform: 'translateY(0)' },
        },
        shimmer: {
          '0%': { backgroundPosition: '-200% 0' },
          '100%': { backgroundPosition: '200% 0' },
        },
      },
      animation: {
        'fade-in': 'fade-in 0.4s ease-out',
        'slide-up': 'slide-up 0.5s ease-out',
        shimmer: 'shimmer 1.5s infinite linear',
      },
    },
  },
  plugins: [],
}
