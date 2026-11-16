import type { FormValues } from "../model/form";
import { utcInput, utcInstant } from "./dates";
import { jsonObject, requestSchema, taskTypeSchema } from "../model/types";
import type { Scheduling, SchedulingRequest } from "../model/types";

export function emptyForm(now = Date.now()): FormValues {
  const future = new Date(now + 3600_000).toISOString().slice(0, 16);
  return {
    name: "",
    description: "",
    taskType: "",
    scheduleType: "ONE_TIME",
    timeZone: "UTC",
    startsAt: future,
    endsAt: "",
    maxOccurrences: "",
    enabled: true,
    message: "",
    to: "",
    subject: "",
    body: "",
    query: "",
    parameters: "",
    interval: "1",
    unit: "SECONDS",
    expression: "0 0 9 ? * MON-FRI",
  };
}
export function editForm(scheduling: Scheduling): FormValues {
  return {
    ...emptyForm(),
    name: scheduling.name,
    description: scheduling.description ?? "",
    taskType: scheduling.taskType,
    scheduleType: scheduling.scheduleType,
    timeZone: scheduling.timeZone,
    startsAt: scheduling.startsAt,
    endsAt: utcInput(scheduling.endsAt),
    maxOccurrences: scheduling.maxOccurrences?.toString() ?? "",
    enabled: scheduling.enabled,
    message: String(scheduling.payload.message ?? ""),
    to: String(scheduling.payload.to ?? ""),
    subject: String(scheduling.payload.subject ?? ""),
    body: String(scheduling.payload.body ?? ""),
    query: String(scheduling.payload.query ?? ""),
    parameters:
      scheduling.payload.parameters === undefined
        ? ""
        : JSON.stringify(scheduling.payload.parameters, null, 2),
    interval: String(scheduling.scheduleConfig.interval ?? 1),
    unit: String(scheduling.scheduleConfig.unit ?? "SECONDS"),
    expression: String(
      scheduling.scheduleConfig.expression ?? "0 0 9 ? * MON-FRI",
    ),
  };
}
function payload(form: FormValues): Record<string, unknown> {
  switch (taskTypeSchema.parse(form.taskType)) {
    case "LOG":
      return { message: form.message };
    case "DUMMY_EMAIL":
      return { to: form.to, subject: form.subject, body: form.body };
    case "DB_QUERY": {
      if (!form.parameters.trim()) return { query: form.query };
      let parameters: unknown;
      try {
        parameters = JSON.parse(form.parameters);
      } catch {
        throw new Error("Parameters must be valid JSON");
      }
      return { query: form.query, parameters: jsonObject.parse(parameters) };
    }
  }
}
export function formToRequest(form: FormValues): SchedulingRequest {
  const scheduleConfig =
    form.scheduleType === "ONE_TIME"
      ? {}
      : form.scheduleType === "CRON"
        ? { expression: form.expression }
        : { interval: Number(form.interval), unit: form.unit };
  return requestSchema.parse({
    name: form.name,
    description: form.description || null,
    taskType: form.taskType,
    payload: payload(form),
    scheduleType: form.scheduleType,
    scheduleConfig,
    timeZone: form.timeZone,
    startsAt: form.startsAt,
    endsAt: utcInstant(form.endsAt),
    maxOccurrences: form.maxOccurrences ? Number(form.maxOccurrences) : null,
    enabled: form.enabled,
  });
}
