import { Button } from "../../../components/atoms/controls";
import type { Mutation } from "../api/api";
import { SchedulingConfirmation } from "./SchedulingConfirmation";
import { SchedulingDetails } from "./SchedulingDetails";
import { SchedulingForm } from "./SchedulingForm";
import type {
  Action,
  SchedulingRequest,
  VersionedScheduling,
} from "../model/types";

export function SchedulingActionContent({
  kind,
  current,
  busy,
  onClose,
  onSave,
  onSubmit,
}: {
  kind: Action;
  current: VersionedScheduling;
  busy: boolean;
  onClose: () => void;
  onSave: (request: SchedulingRequest) => void;
  onSubmit: (action: Mutation) => void;
}) {
  switch (kind) {
    case "view":
      return (
        <>
          <SchedulingDetails scheduling={current.scheduling} />
          <div className="dialog-footer">
            <Button onClick={onClose}>Close</Button>
          </div>
        </>
      );
    case "edit":
      return (
        <SchedulingForm
          scheduling={current.scheduling}
          busy={busy}
          onCancel={onClose}
          onSubmit={onSave}
        />
      );
    case "delete":
    case "toggle":
      return (
        <SchedulingConfirmation
          kind={kind}
          current={current}
          busy={busy}
          onClose={onClose}
          onSubmit={onSubmit}
        />
      );
  }
}
