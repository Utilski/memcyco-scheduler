import type { JsonValue, Scheduling } from "../model/types";

export function humanize(value: string): string {
  return value
    .toLowerCase()
    .split("_")
    .map((word) => word[0]?.toUpperCase() + word.slice(1))
    .join(" ");
}
export function formatInstant(value: string | null, timeZone = "UTC"): string {
  if (!value) return "No next run";
  return new Intl.DateTimeFormat("en-GB", {
    dateStyle: "medium",
    timeStyle: "medium",
    timeZone,
  }).format(new Date(value));
}
export function formatLocal(value: string): string {
  return value.replace("T", " ");
}
export function jsonSummary(value: Record<string, JsonValue>): string {
  return JSON.stringify(value);
}
export function scheduleSummary(scheduling: Scheduling): string {
  if (scheduling.scheduleType === "ONE_TIME") return "One time";
  if (scheduling.scheduleType === "CRON")
    return String(scheduling.scheduleConfig.expression ?? "");
  return `Every ${scheduling.scheduleConfig.interval} ${String(scheduling.scheduleConfig.unit).toLowerCase()}`;
}
