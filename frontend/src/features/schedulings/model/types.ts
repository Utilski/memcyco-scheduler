import { z } from "zod";
import { localDateTimeSchema } from "../utils/dates";

export type JsonValue =
  string | number | boolean | null | JsonValue[] | { [key: string]: JsonValue };
const jsonValue: z.ZodType<JsonValue> = z.lazy(() =>
  z.union([
    z.string(),
    z.number().finite(),
    z.boolean(),
    z.null(),
    z.array(jsonValue),
    z.record(z.string(), jsonValue),
  ]),
);
export const jsonObject = z.record(z.string(), jsonValue);
export const taskTypeSchema = z.enum(["LOG", "DUMMY_EMAIL", "DB_QUERY"]);
export const scheduleTypeSchema = z.enum([
  "ONE_TIME",
  "SIMPLE_INTERVAL",
  "CALENDAR_INTERVAL",
  "CRON",
]);
export type TaskType = z.infer<typeof taskTypeSchema>;
export type ScheduleType = z.infer<typeof scheduleTypeSchema>;
const integer = z.number().int().min(0).max(Number.MAX_SAFE_INTEGER);
const instant = z.iso.datetime({ offset: true, precision: null });
const nonblank = z
  .string()
  .refine((value) => value.trim().length > 0, "Must not be blank");
const metadata = z.object({
  name: nonblank.max(255),
  description: z.string().nullable(),
  timeZone: z
    .string()
    .min(1)
    .refine((zone) => {
      try {
        new Intl.DateTimeFormat("en", { timeZone: zone });
        return true;
      } catch {
        return false;
      }
    }, "Enter a valid IANA timezone"),
  startsAt: localDateTimeSchema,
  endsAt: instant.nullable(),
  maxOccurrences: integer.positive().nullable(),
  enabled: z.boolean(),
});
const task = z.discriminatedUnion("taskType", [
  z.object({
    taskType: z.literal("LOG"),
    payload: z.object({ message: nonblank }).strict(),
  }),
  z.object({
    taskType: z.literal("DUMMY_EMAIL"),
    payload: z
      .object({ to: z.email(), subject: nonblank, body: nonblank })
      .strict(),
  }),
  z.object({
    taskType: z.literal("DB_QUERY"),
    payload: z
      .object({ query: nonblank, parameters: jsonObject.optional() })
      .strict(),
  }),
]);
const interval = integer.positive().max(2147483647);
const schedule = z.discriminatedUnion("scheduleType", [
  z.object({
    scheduleType: z.literal("ONE_TIME"),
    scheduleConfig: z.object({}).strict(),
  }),
  z.object({
    scheduleType: z.literal("SIMPLE_INTERVAL"),
    scheduleConfig: z.object({
      interval,
      unit: z.enum(["SECONDS", "MINUTES", "HOURS"]),
    }),
  }),
  z.object({
    scheduleType: z.literal("CALENDAR_INTERVAL"),
    scheduleConfig: z.object({
      interval,
      unit: z.enum(["DAY", "WEEK", "MONTH", "YEAR"]),
    }),
  }),
  z.object({
    scheduleType: z.literal("CRON"),
    scheduleConfig: z.object({ expression: nonblank }),
  }),
]);
export const requestSchema = z
  .intersection(metadata, z.intersection(task, schedule))
  .superRefine((value, context) => {
    if (value.scheduleType === "ONE_TIME" && value.maxOccurrences !== null) {
      context.addIssue({
        code: "custom",
        path: ["maxOccurrences"],
        message: "One-time schedules cannot have an occurrence limit",
      });
    }
  });
export type SchedulingRequest = z.infer<typeof requestSchema>;
// Task names come from the backend. Unknown future task payloads can still be viewed safely.
export const schedulingSchema = metadata
  .extend({
    id: z.uuid(),
    taskType: z.string(),
    payload: jsonObject,
    scheduleType: scheduleTypeSchema,
    scheduleConfig: jsonObject,
    nextRunAt: instant.nullable(),
    dispatchedCount: integer,
    createdAt: instant,
    updatedAt: instant,
    version: integer,
  })
  .superRefine((value, context) => {
    const validations = [
      schedule.safeParse(value),
      taskTypeSchema.safeParse(value.taskType).success
        ? task.safeParse(value)
        : null,
    ];
    for (const result of validations) {
      if (result && !result.success) {
        for (const issue of result.error.issues) {
          context.addIssue({
            code: "custom",
            path: issue.path,
            message: issue.message,
          });
        }
      }
    }
  });
export type Scheduling = z.infer<typeof schedulingSchema>;
export const pageSchema = z.object({
  items: z.array(schedulingSchema),
  page: integer,
  size: integer.positive(),
  totalItems: integer,
  totalPages: integer,
});
export type SchedulingPage = z.infer<typeof pageSchema>;
export const sortFields = [
  "name",
  "taskType",
  "scheduleType",
  "enabled",
  "startsAt",
  "nextRunAt",
  "createdAt",
  "updatedAt",
] as const;
export const sortSchema = z.enum(sortFields);
export type SortField = z.infer<typeof sortSchema>;
export type ListQuery = Readonly<{
  page: number;
  size: number;
  search: string;
  enabled: "" | "true" | "false";
  sort: SortField;
  direction: "asc" | "desc";
}>;
export const initialQuery: ListQuery = {
  page: 0,
  size: 20,
  search: "",
  enabled: "",
  sort: "createdAt",
  direction: "asc",
};
export type Action = "view" | "edit" | "toggle" | "delete";
export type DialogState =
  { kind: "closed" } | { kind: "create" } | { kind: Action; id: string };
export type VersionedScheduling = Readonly<{
  scheduling: Scheduling;
  etag: string;
}>;
