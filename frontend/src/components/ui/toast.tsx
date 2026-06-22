/**
 * 统一的底部 Toast。
 *
 * 替代此前在 ai-review / dashboard / dev-logs / project-intelligence / tasks
 * 五个页面里逐字复制的 `fixed bottom-5 left-1/2 ...` 错误/提示条。
 */
export function Toast({ error, notice }: { error?: string; notice?: string }) {
  if (!error && !notice) {
    return null;
  }
  return (
    <>
      {error ? (
        <div className="fixed bottom-5 left-1/2 z-50 -translate-x-1/2 rounded-field border border-danger/30 bg-danger-soft px-4 py-3 text-sm text-danger-fg shadow-cardLg">
          {error}
        </div>
      ) : null}
      {notice ? (
        <div className="fixed bottom-5 left-1/2 z-50 -translate-x-1/2 rounded-field border border-success/30 bg-success-soft px-4 py-3 text-sm text-success-fg shadow-cardLg">
          {notice}
        </div>
      ) : null}
    </>
  );
}
