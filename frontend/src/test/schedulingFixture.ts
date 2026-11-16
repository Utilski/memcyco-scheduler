import { schedulingSchema } from "../features/schedulings/model/types";

export const schedulingFixture = schedulingSchema.parse({
  id: "10000000-0000-4000-8000-000000000001",
  name: "Example",
  description: null,
  taskType: "LOG",
  payload: { message: "hello" },
  scheduleType: "ONE_TIME",
  scheduleConfig: {},
  timeZone: "Asia/Jerusalem",
  startsAt: "2026-12-01T09:15:30.123",
  endsAt: "2026-12-02T10:00:00.456Z",
  maxOccurrences: null,
  enabled: false,
  nextRunAt: null,
  dispatchedCount: 0,
  createdAt: "2026-11-16T00:00:00Z",
  updatedAt: "2026-11-16T00:00:00Z",
  version: 0,
});
