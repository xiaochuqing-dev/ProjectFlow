import type { Config } from "tailwindcss";

const config: Config = {
  content: ["./src/**/*.{ts,tsx}"],
  theme: {
    extend: {
      colors: {
        // 背景层级：页面底 > 内嵌块 > 卡片白
        surface: "#f8fafc",
        surfaceAlt: "#f1f5f9",
        elevated: "#ffffff",
        // 文字
        ink: "#0f172a",
        body: "#334155",
        muted: "#64748b",
        // 描边
        line: "#e2e8f0",
        lineStrong: "#cbd5e1",
        // 品牌 —— 靛蓝（主操作色）
        brand: {
          DEFAULT: "#4f46e5",
          hover: "#4338ca",
          soft: "#eef2ff",
          ring: "#c7d2fe",
        },
        // 语义色：统一此前的 amber/emerald/rose 散用
        success: { soft: "#ecfdf5", fg: "#047857" },
        warning: { soft: "#fffbeb", fg: "#b45309" },
        danger: { soft: "#fef2f2", fg: "#b91c1c" },
      },
      borderRadius: {
        card: "0.75rem",
        field: "0.5rem",
      },
      boxShadow: {
        // 克制的卡片浮起（新页面使用）
        card: "0 1px 3px rgba(15, 23, 42, 0.06), 0 1px 2px rgba(15, 23, 42, 0.04)",
        cardLg: "0 10px 30px rgba(15, 23, 42, 0.08), 0 2px 6px rgba(15, 23, 42, 0.04)",
        focus: "0 0 0 3px rgba(79, 70, 229, 0.15)",
        // 旧 token 别名，保留以避免一次波及所有旧页面
        panel: "0 18px 60px rgba(16, 24, 40, 0.08)",
      },
    },
  },
  plugins: [],
};

export default config;
