"use client";

import { useEffect } from "react";

/**
 * Next.js 全局错误边界。
 *
 * 默认 UI 只显示 "This page couldn't load / Reload to try again, or go back."，
 * 看不到真正的错误。这里把错误详情写入 localStorage，方便定位偶发的客户端渲染异常
 * （通常是 client-side navigation 时 chunk 加载失败或组件访问 undefined 字段）。
 */
export default function GlobalError({
  error,
  reset,
}: {
  error: Error & { digest?: string };
  reset: () => void;
}) {
  useEffect(() => {
    try {
      const record = {
        at: new Date().toISOString(),
        message: error?.message,
        stack: error?.stack,
        digest: error?.digest,
        href: typeof window !== "undefined" ? window.location.href : undefined,
      };
      const key = "projectflow:global-error";
      const prev = JSON.parse(localStorage.getItem(key) || "[]");
      prev.push(record);
      // 只保留最近 10 条，避免无限增长
      localStorage.setItem(key, JSON.stringify(prev.slice(-10)));
    } catch {
      // localStorage 不可用时忽略
    }
  }, [error]);

  return (
    <html lang="zh-CN">
      <body>
        <div style={{ display: "grid", placeItems: "center", minHeight: "100vh", margin: 0, fontFamily: "system-ui, sans-serif", color: "#1f2937", textAlign: "center" }}>
          <div style={{ maxWidth: 420, padding: "2rem" }}>
            <h1 style={{ fontSize: "1.25rem", fontWeight: 600, marginBottom: "0.5rem" }}>
              页面加载失败
            </h1>
            <p style={{ fontSize: "0.875rem", color: "#6b7280", marginBottom: "1.5rem" }}>
              重新加载试试，或返回上一页。错误已记录，可稍后在设置页查看。
            </p>
            <div style={{ display: "flex", gap: "0.75rem", justifyContent: "center" }}>
              <button
                type="button"
                onClick={() => reset()}
                style={{ padding: "0.5rem 1.25rem", borderRadius: "0.5rem", border: "1px solid #d1d5db", background: "#111827", color: "#fff", cursor: "pointer", fontSize: "0.875rem" }}
              >
                Reload
              </button>
              <button
                type="button"
                onClick={() => {
                  if (typeof window !== "undefined") {
                    if (window.history.length > 1) {
                      window.history.back();
                    } else {
                      window.location.href = "/";
                    }
                  }
                }}
                style={{ padding: "0.5rem 1.25rem", borderRadius: "0.5rem", border: "1px solid #d1d5db", background: "#fff", color: "#111827", cursor: "pointer", fontSize: "0.875rem" }}
              >
                Back
              </button>
            </div>
          </div>
        </div>
      </body>
    </html>
  );
}
