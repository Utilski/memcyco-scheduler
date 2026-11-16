import { Dialog } from "../../../components/molecules/Dialog";
import { SchedulingActionContent } from "./SchedulingActionContent";
import { MutationFailure, RecordFailure } from "./SchedulingFailure";
import { SchedulingForm } from "./SchedulingForm";
import { useSchedulingAction } from "../hooks/useSchedulingAction";
import type { OpenDialog } from "../hooks/useSchedulingAction";

const titles = {
  create: "Create scheduling",
  edit: "Edit scheduling",
  view: "Scheduling details",
  toggle: "Change enabled state",
  delete: "Delete scheduling",
};
export function SchedulingDialog({
  state,
  onClose,
  onSuccess,
}: {
  state: OpenDialog;
  onClose: () => void;
  onSuccess: (message: string) => void;
}) {
  const action = useSchedulingAction(state, onClose, onSuccess);
  const { record, mutation } = action;
  return (
    <Dialog
      title={titles[state.kind]}
      onClose={onClose}
      busy={mutation.isPending}
    >
      {mutation.error && (
        <MutationFailure error={mutation.error} onReload={action.reload} />
      )}
      {state.kind === "create" ? (
        <SchedulingForm
          busy={mutation.isPending}
          onCancel={onClose}
          onSubmit={action.save}
        />
      ) : record.isFetching ? (
        <p role="status">Loading latest scheduling…</p>
      ) : record.error ? (
        <RecordFailure error={record.error} onRetry={action.retryRecord} />
      ) : (
        record.data && (
          <SchedulingActionContent
            key={`${record.data.scheduling.id}-${record.dataUpdatedAt}`}
            kind={state.kind}
            current={record.data}
            busy={mutation.isPending}
            onClose={onClose}
            onSave={action.save}
            onSubmit={action.submit}
          />
        )
      )}
    </Dialog>
  );
}
