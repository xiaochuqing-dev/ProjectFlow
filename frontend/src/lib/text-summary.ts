export function firstUsefulLine(value: string | undefined, fallback = "") {
  if (!value) {
    return fallback;
  }
  return value.split(/\r?\n/)
    .map((line) => line.replace(/^[-*#>\s]+/, "").trim())
    .find((line) => line && !line.startsWith("暂无")) ?? fallback;
}
