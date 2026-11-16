import type { ScheduleType } from "./types";

export type FormValues = {
  name: string;
  description: string;
  taskType: string;
  scheduleType: ScheduleType;
  timeZone: string;
  startsAt: string;
  endsAt: string;
  maxOccurrences: string;
  enabled: boolean;
  message: string;
  to: string;
  subject: string;
  body: string;
  query: string;
  parameters: string;
  interval: string;
  unit: string;
  expression: string;
};

export type UpdateForm = <Key extends keyof FormValues>(
  key: Key,
  value: FormValues[Key],
) => void;
