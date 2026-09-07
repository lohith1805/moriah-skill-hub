/** @type {import('tailwindcss').Config} */
export default {
  content: ["./index.html", "./src/**/*.{js,jsx}"],
  theme: {
    extend: {
      colors: {
        primary: {
          50: "#EEF2F7",
          100: "#D6E0EB",
          200: "#AFC3D8",
          300: "#7E9DBD",
          400: "#4A6E96",
          500: "#1E4A78",
          600: "#163A5F",
          700: "#0D2845",
          800: "#0A1F38",
          900: "#071627",
        },
        gold: {
          50: "#FEFAEE",
          100: "#FCF0C8",
          200: "#F8E092",
          300: "#F3D05C",
          400: "#F0C93D",
          500: "#EBB80D",
          600: "#B8890A",
          700: "#8F6A08",
          800: "#664C06",
          900: "#453304",
        },
        cream: {
          DEFAULT: "#F7F5F0",
          50: "#FDFCFA",
          100: "#F7F5F0",
          200: "#EFEBE1",
        },
        ink: {
          900: "#14213D",
          700: "#2B354A",
          500: "#5B6472",
          400: "#8A93A0",
        },
        border: {
          DEFAULT: "#E4E1D8",
          dark: "#D3CFC3",
        },
        success: { 50: "#EAF6EE", 500: "#1E7A46", 600: "#186238" },
        warning: { 50: "#FDF3E7", 500: "#C67C0A", 600: "#9C6208" },
        error: { 50: "#FBEAE8", 500: "#C0392B", 600: "#9A2E22" },
        info: { 50: "#E9F2FA", 500: "#2470B8", 600: "#1C5A92" },
      },
      fontFamily: {
        display: ["'Sora'", "system-ui", "sans-serif"],
        sans: ["'Inter'", "system-ui", "sans-serif"],
      },
      boxShadow: {
        card: "0 1px 2px 0 rgba(13,40,69,0.06), 0 1px 3px 0 rgba(13,40,69,0.08)",
        popover: "0 8px 24px -4px rgba(13,40,69,0.18)",
      },
      borderRadius: {
        xl: "0.875rem",
      },
    },
  },
  plugins: [],
};
