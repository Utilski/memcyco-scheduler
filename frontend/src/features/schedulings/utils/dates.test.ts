import { expect, it } from "vitest";
import { localDateTimeSchema, utcInstant } from "./dates";

it.each([
  "2026-02-31T09:00",
  "2025-02-29T09:00",
  "2026-04-31T12:00",
  "2026-01-01T24:00",
  "2026-01-01T09:60",
  "0999-01-01T09:00",
  "2026-01-01T09:00:00.000001",
])(
  "rejects invalid local dates without JavaScript normalization: %s",
  (value) => {
    expect(() => localDateTimeSchema.parse(value)).toThrow();
    expect(() => utcInstant(value)).toThrow();
  },
);
it("accepts leap days and preserves valid milliseconds", () => {
  expect(utcInstant("2028-02-29T23:59:59.123")).toBe(
    "2028-02-29T23:59:59.123Z",
  );
  expect(localDateTimeSchema.parse("2028-02-29T09:00")).toBe(
    "2028-02-29T09:00",
  );
});
