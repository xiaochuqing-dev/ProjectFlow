"use client";

import { useEffect, useRef } from "react";

export function useAutoDismissNotice(error: string, notice: string, clear: () => void, delayMs = 4200) {
  const clearRef = useRef(clear);

  useEffect(() => {
    clearRef.current = clear;
  }, [clear]);

  useEffect(() => {
    if (!notice && !error) {
      return;
    }
    const timeout = window.setTimeout(() => clearRef.current(), delayMs);
    return () => window.clearTimeout(timeout);
  }, [delayMs, error, notice]);
}
