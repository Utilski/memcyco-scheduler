import { ZodError } from "zod";
export function errorMessage(error: unknown): string {
  if (error instanceof ZodError)
    return error.issues
      .map((issue) => `${issue.path.join(".") || "Value"}: ${issue.message}`)
      .join("; ");
  return error instanceof Error
    ? error.message
    : "Something went wrong. Please retry.";
}
