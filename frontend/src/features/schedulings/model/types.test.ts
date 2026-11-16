import { expect, it } from "vitest";
import { schedulingFixture } from "../../../test/schedulingFixture";
import { schedulingSchema } from "./types";

it("rejects malformed known payloads before they can populate an edit form", () => {
  expect(() =>
    schedulingSchema.parse({ ...schedulingFixture, payload: { message: 42 } }),
  ).toThrow();
  expect(() =>
    schedulingSchema.parse({
      ...schedulingFixture,
      scheduleType: "CRON",
      scheduleConfig: {},
    }),
  ).toThrow();
});
it("preserves viewability of unknown future task types without pretending to validate their shape", () => {
  expect(
    schedulingSchema.parse({
      ...schedulingFixture,
      taskType: "FUTURE_TASK",
      payload: { futureField: [1, true] },
    }).taskType,
  ).toBe("FUTURE_TASK");
});
