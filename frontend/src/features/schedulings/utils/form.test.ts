import { utcInput, utcInstant } from "./dates";
import { describe, expect, it } from "vitest";
import { editForm, emptyForm, formToRequest } from "./form";
import { schedulingFixture as fixture } from "../../../test/schedulingFixture";

describe("form conversion", () => {
  it("preserves local start, explicit UTC end, and milliseconds independently of browser timezone", () => {
    const request = formToRequest(editForm(fixture));
    expect(request.startsAt).toBe(fixture.startsAt);
    expect(request.endsAt).toBe(fixture.endsAt);
    expect(request).not.toHaveProperty("id");
    expect(request).not.toHaveProperty("version");
  });
  it("normalizes offset instants and optional values", () => {
    expect(utcInput("2026-01-01T09:00:00+02:00")).toBe(
      "2026-01-01T07:00:00.000",
    );
    expect(utcInstant("2026-01-01T07:00")).toBe("2026-01-01T07:00:00.000Z");
    expect(utcInstant("")).toBeNull();
    expect(() => utcInstant("2026-01-01T07:00:00.000001")).toThrow();
  });
  it("builds native interval and simulated query payloads", () => {
    const form = {
      ...editForm(fixture),
      taskType: "DB_QUERY",
      query: "dummy",
      parameters: '{"id":1}',
      scheduleType: "CALENDAR_INTERVAL" as const,
      interval: "2",
      unit: "DAY",
    };
    expect(formToRequest(form)).toMatchObject({
      payload: { query: "dummy", parameters: { id: 1 } },
      scheduleConfig: { unit: "DAY", interval: 2 },
    });
    expect(() => formToRequest({ ...form, parameters: "{bad" })).toThrow(
      "valid JSON",
    );
    expect(() => formToRequest({ ...form, parameters: "[]" })).toThrow();
  });
  it("rejects unknown task types, unsafe limits, and wrong interval units", () => {
    const form = { ...editForm(fixture), taskType: "FUTURE_TASK" };
    expect(() => formToRequest(form)).toThrow();
    expect(() =>
      formToRequest({
        ...editForm(fixture),
        maxOccurrences: "9007199254740992",
      }),
    ).toThrow();
    expect(() =>
      formToRequest({
        ...editForm(fixture),
        scheduleType: "SIMPLE_INTERVAL",
        interval: "1",
        unit: "DAY",
      }),
    ).toThrow();
    expect(emptyForm().taskType).toBe("");
  });
});
