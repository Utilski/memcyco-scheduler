import { z } from "zod";

// Validate calendar dates before Date conversion, which otherwise normalizes February 31.
export const localDateTimeSchema = z
  .string()
  .regex(
    /^\d{4}-\d\d-\d\dT(?:[01]\d|2[0-3]):[0-5]\d(?::[0-5]\d(?:\.\d{1,3})?)?$/,
    "Enter a valid local date and time (at most milliseconds)",
  )
  .refine(
    (value) => z.iso.date().safeParse(value.slice(0, 10)).success,
    "Enter an existing calendar date",
  )
  .refine(
    (value) => Number(value.slice(0, 4)) >= 1000,
    "Dates must be in years 1000–9999",
  );

export function utcInput(value: string | null): string {
  return value ? new Date(value).toISOString().slice(0, -1) : "";
}
export function utcInstant(value: string): string | null {
  if (!value) return null;
  return new Date(`${localDateTimeSchema.parse(value)}Z`).toISOString();
}
