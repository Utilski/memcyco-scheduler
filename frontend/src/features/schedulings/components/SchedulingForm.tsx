import { useState } from "react";
import {
  Button,
  Input,
  Select,
  Textarea,
} from "../../../components/atoms/controls";
import { Field } from "../../../components/molecules/Field";
import { errorMessage } from "../../../lib/errors";
import { emptyForm, editForm, formToRequest } from "../utils/form";
import type { FormValues } from "../model/form";
import { humanize } from "../utils/format";
import { useTaskTypes } from "../hooks/queries";
import { TaskFields } from "./TaskFields";
import { TimingFields } from "./TimingFields";
import { taskTypeSchema } from "../model/types";
import type { Scheduling, SchedulingRequest } from "../model/types";

export function SchedulingForm({
  scheduling,
  busy,
  onCancel,
  onSubmit,
}: {
  scheduling?: Scheduling;
  busy: boolean;
  onCancel: () => void;
  onSubmit: (request: SchedulingRequest) => void;
}) {
  const [form, setForm] = useState(() =>
    scheduling ? editForm(scheduling) : emptyForm(),
  );
  const [validationError, setValidationError] = useState("");
  const taskTypes = useTaskTypes();
  function update<Key extends keyof FormValues>(
    key: Key,
    value: FormValues[Key],
  ) {
    setForm((previous) => ({ ...previous, [key]: value }));
    setValidationError("");
  }
  const supported = taskTypeSchema.safeParse(form.taskType).success;
  return (
    <form
      onSubmit={(event) => {
        event.preventDefault();
        try {
          const request = formToRequest(form);
          setValidationError("");
          onSubmit(request);
        } catch (error) {
          setValidationError(errorMessage(error));
        }
      }}
    >
      {validationError && (
        <p role="alert" className="error">
          {validationError}
        </p>
      )}
      <fieldset disabled={busy}>
        <div className="section-label">01 / DETAILS</div>
        <Field label="Name">
          <Input
            autoFocus
            required
            maxLength={255}
            value={form.name}
            onChange={(event) => update("name", event.target.value)}
          />
        </Field>
        <Field label="Description (optional)">
          <Textarea
            value={form.description}
            onChange={(event) => update("description", event.target.value)}
          />
        </Field>
        <div className="section-label">02 / TASK</div>
        {taskTypes.isPending && <p role="status">Loading task types…</p>}
        {taskTypes.isError && (
          <div role="alert" className="error">
            Could not load task types.{" "}
            <Button onClick={() => void taskTypes.refetch()}>
              Retry task types
            </Button>
          </div>
        )}
        <Field label="Task type">
          <Select
            required
            value={form.taskType}
            disabled={!taskTypes.data}
            onChange={(event) => update("taskType", event.target.value)}
          >
            <option value="">Select task type</option>
            {taskTypes.data?.map((type) => (
              <option key={type} value={type}>
                {humanize(type)}
              </option>
            ))}
          </Select>
        </Field>
        {form.taskType && !supported && (
          <p role="alert" className="error">
            This task type is not supported by this frontend. Its payload can be
            viewed but not edited.
          </p>
        )}
        <TaskFields form={form} update={update} />
        <div className="section-label">03 / TIMING</div>
        <TimingFields form={form} update={update} />
        <label className="checkbox">
          <Input
            type="checkbox"
            checked={form.enabled}
            onChange={(event) => update("enabled", event.target.checked)}
          />
          Enabled
        </label>
      </fieldset>
      <div className="dialog-footer">
        <Button onClick={onCancel} disabled={busy}>
          Cancel
        </Button>
        <Button
          variant="primary"
          type="submit"
          disabled={
            busy ||
            !supported ||
            !taskTypes.data ||
            !taskTypes.data.includes(form.taskType)
          }
        >
          {busy ? "Saving…" : scheduling ? "Save changes" : "Create scheduling"}
        </Button>
      </div>
    </form>
  );
}
