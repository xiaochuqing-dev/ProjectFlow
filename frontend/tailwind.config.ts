import type { Config } from "tailwindcss";

const config: Config = {
  content: ["./src/**/*.{ts,tsx}"],
  theme: {
    extend: {
      colors: {
        surface: "#f6f8fb",
        ink: "#101828",
        muted: "#667085",
        line: "#dbe3ef",
        brand: "#1f6fff",
      },
      boxShadow: {
        panel: "0 18px 60px rgba(16, 24, 40, 0.08)",
      },
    },
  },
  plugins: [],
};

export default config;
