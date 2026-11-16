import { useQuery, useQueryClient } from "@tanstack/react-query";
import { ApiError, getScheduling } from "../api/api";
import type { Mutation } from "../api/api";
import { useSchedulingMutation } from "./queries";
import type { DialogState, SchedulingRequest } from "../model/types";

export type OpenDialog = Exclude<DialogState, { kind: "closed" }>;
const successMessages = {
  create: "Scheduling created.",
  edit: "Scheduling updated.",
  toggle: "Scheduling updated.",
  delete: "Scheduling deleted.",
};

export function useSchedulingAction(
  state: OpenDialog,
  onClose: () => void,
  onSuccess: (message: string) => void,
) {
  const client = useQueryClient();
  const mutation = useSchedulingMutation();
  const id = "id" in state ? state.id : null;
  const record = useQuery({
    queryKey: ["scheduling-action", id],
    enabled: id !== null,
    queryFn: ({ signal }) => {
      if (!id) throw new Error("No schedule selected");
      return getScheduling(id, signal);
    },
    staleTime: 0,
    refetchOnWindowFocus: false,
    refetchOnReconnect: false,
    refetchOnMount: "always",
    retry: false,
  });
  function closeMissing() {
    void client.invalidateQueries({ queryKey: ["schedulings"] });
    onSuccess("This scheduling no longer exists.");
    onClose();
  }
  function submit(action: Mutation) {
    mutation.mutate(action, {
      onSuccess: () => {
        onSuccess(successMessages[action.kind]);
        onClose();
      },
      onError: (error) => {
        if (error instanceof ApiError && error.status === 404) closeMissing();
      },
    });
  }
  function save(request: SchedulingRequest) {
    if (state.kind === "create") submit({ kind: "create", request });
    else if (id && record.data)
      submit({ kind: "edit", id, etag: record.data.etag, request });
  }
  function reload() {
    mutation.reset();
    void record.refetch();
  }
  function retryRecord() {
    if (record.error instanceof ApiError && record.error.status === 404)
      closeMissing();
    else void record.refetch();
  }
  return { record, mutation, submit, save, reload, retryRecord };
}
