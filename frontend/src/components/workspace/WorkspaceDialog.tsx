"use client";

import { useEffect, useRef, type ReactNode } from "react";
import { X } from "lucide-react";

// Native modal dialogs provide focus containment, Escape and focus restoration.
export function WorkspaceDialog({ title, labelledBy, onClose, busy = false, className = "", children }: {
  title: string; labelledBy?: string; onClose: () => void; busy?: boolean; className?: string; children: ReactNode;
}) {
  const dialog = useRef<HTMLDialogElement>(null);
  useEffect(() => {
    const element = dialog.current;
    element?.showModal();
    return () => { element?.close(); };
  }, []);
  return (
    <dialog ref={dialog} className={`pf-dialog ${className}`} aria-label={title} aria-labelledby={labelledBy}
      onCancel={(event) => { if (busy) event.preventDefault(); }}
      onClose={() => {
        // React Strict Mode may close and reopen the same native element during
        // effect verification. Ignore the queued close event after it reopened.
        if (!dialog.current?.open) onClose();
      }}>
      <header className="pf-dialog-heading">
        <strong>{title}</strong>
        <button type="button" className="pf-icon-button" disabled={busy} aria-label={`关闭${title}`}
          onClick={() => dialog.current?.close()}><X size={20} /></button>
      </header>
      {children}
    </dialog>
  );
}
