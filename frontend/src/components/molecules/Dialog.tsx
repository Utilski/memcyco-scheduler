import { useEffect, useId, useRef } from "react";
import type { ReactNode } from "react";
import { Button } from "../atoms/controls";

export function Dialog({
  title,
  description,
  children,
  onClose,
  busy = false,
}: {
  title: string;
  description?: string;
  children: ReactNode;
  onClose: () => void;
  busy?: boolean;
}) {
  const ref = useRef<HTMLDialogElement>(null);
  const titleId = useId();
  const descriptionId = useId();
  useEffect(() => {
    const dialog = ref.current;
    const previous = document.activeElement;
    dialog?.showModal();
    return () => {
      dialog?.close();
      if (previous instanceof HTMLElement) previous.focus();
    };
  }, []);
  return (
    <dialog
      ref={ref}
      aria-labelledby={titleId}
      aria-describedby={description ? descriptionId : undefined}
      onCancel={(event) => {
        event.preventDefault();
        if (!busy) onClose();
      }}
    >
      <div className="dialog-heading">
        <div>
          <p className="eyebrow">SCHEDULING / WORKSPACE</p>
          <h2 id={titleId}>{title}</h2>
          {description && (
            <p id={descriptionId} className="muted">
              {description}
            </p>
          )}
        </div>
        <Button
          variant="quiet"
          aria-label="Close dialog"
          onClick={onClose}
          disabled={busy}
        >
          ✕
        </Button>
      </div>
      {children}
    </dialog>
  );
}
