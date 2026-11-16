import { Button } from "../../../components/atoms/controls";
import type { Mutation } from "../api/api";
import type { VersionedScheduling } from "../model/types";

export function SchedulingConfirmation({
  kind,
  current,
  busy,
  onClose,
  onSubmit,
}: {
  kind: "delete" | "toggle";
  current: VersionedScheduling;
  busy: boolean;
  onClose: () => void;
  onSubmit: (action: Mutation) => void;
}) {
  const { scheduling, etag } = current;
  const label =
    kind === "delete"
      ? "Delete scheduling"
      : scheduling.enabled
        ? "Disable scheduling"
        : "Enable scheduling";
  return (
    <form
      onSubmit={(event) => {
        event.preventDefault();
        if (kind === "delete") onSubmit({ kind, id: scheduling.id, etag });
        else
          onSubmit({
            kind,
            id: scheduling.id,
            etag,
            enabled: !scheduling.enabled,
          });
      }}
    >
      <p className="confirmation">
        {kind === "delete"
          ? `Permanently delete “${scheduling.name}”?`
          : `${scheduling.enabled ? "Disable" : "Enable"} “${scheduling.name}”?`}
      </p>
      <p className="muted">
        {kind === "delete"
          ? "The definition will be removed and pending work canceled. Execution history is retained."
          : "Changing enabled state invalidates pending work. Already-running work may finish; completed schedules keep their progress."}
      </p>
      <div className="dialog-footer">
        <Button onClick={onClose} disabled={busy}>
          Cancel
        </Button>
        <Button
          type="submit"
          variant={kind === "delete" ? "danger" : "primary"}
          disabled={busy}
        >
          {busy ? "Saving…" : label}
        </Button>
      </div>
    </form>
  );
}
