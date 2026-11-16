import { Field } from "../../../components/molecules/Field";
import { Input, Textarea } from "../../../components/atoms/controls";
import type { FormValues, UpdateForm } from "../model/form";

export function TaskFields({
  form,
  update,
}: {
  form: FormValues;
  update: UpdateForm;
}) {
  switch (form.taskType) {
    case "LOG":
      return (
        <Field label="Log message">
          <Textarea
            required
            value={form.message}
            onChange={(event) => update("message", event.target.value)}
          />
        </Field>
      );
    case "DUMMY_EMAIL":
      return (
        <>
          <p className="notice">
            Email delivery is simulated. No email will be sent.
          </p>
          <Field label="Recipient">
            <Input
              type="email"
              required
              value={form.to}
              onChange={(event) => update("to", event.target.value)}
            />
          </Field>
          <Field label="Subject">
            <Input
              required
              value={form.subject}
              onChange={(event) => update("subject", event.target.value)}
            />
          </Field>
          <Field label="Email body">
            <Textarea
              required
              value={form.body}
              onChange={(event) => update("body", event.target.value)}
            />
          </Field>
        </>
      );
    case "DB_QUERY":
      return (
        <>
          <p className="notice">
            Queries are simulated and never executed against the database.
          </p>
          <Field label="Query text">
            <Textarea
              required
              value={form.query}
              onChange={(event) => update("query", event.target.value)}
            />
          </Field>
          <Field label="Parameters (JSON object, optional)">
            <Textarea
              className="mono"
              placeholder={'{ "id": 1 }'}
              value={form.parameters}
              onChange={(event) => update("parameters", event.target.value)}
            />
          </Field>
        </>
      );
    default:
      return (
        <p className="notice">
          Select a supported task type to configure its payload.
        </p>
      );
  }
}
