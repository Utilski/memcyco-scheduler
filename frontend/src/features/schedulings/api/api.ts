import { z } from "zod";
import { pageSchema, schedulingSchema } from "../model/types";
import type {
  ListQuery,
  SchedulingRequest,
  VersionedScheduling,
} from "../model/types";

const base = (
  import.meta.env.VITE_API_BASE_URL ?? "http://localhost:8080"
).replace(/\/$/, "");
const problemSchema = z.object({
  detail: z.string().optional(),
  title: z.string().optional(),
});
export class ApiError extends Error {
  readonly status: number;
  constructor(status: number, message: string) {
    super(message);
    this.name = "ApiError";
    this.status = status;
  }
}
async function checked(path: string, init?: RequestInit): Promise<Response> {
  let response: Response;
  try {
    response = await fetch(`${base}/api/schedulings${path}`, init);
  } catch (error) {
    if (error instanceof DOMException && error.name === "AbortError")
      throw error;
    throw new Error(
      "Cannot reach the scheduler. Check the backend connection and retry.",
      { cause: error },
    );
  }
  if (!response.ok) {
    const body: unknown = await response.json().catch(() => null);
    const parsed = problemSchema.safeParse(body);
    throw new ApiError(
      response.status,
      parsed.success
        ? (parsed.data.detail ?? parsed.data.title ?? "Request failed")
        : `Request failed (${response.status})`,
    );
  }
  return response;
}
export async function listSchedulings(query: ListQuery, signal?: AbortSignal) {
  const params = new URLSearchParams({
    page: String(query.page),
    size: String(query.size),
    sort: query.sort,
    direction: query.direction,
  });
  if (query.search) params.set("search", query.search);
  if (query.enabled) params.set("enabled", query.enabled);
  return pageSchema.parse(
    await (await checked(`?${params}`, { signal })).json(),
  );
}
export async function loadTaskTypes(signal?: AbortSignal): Promise<string[]> {
  return z
    .array(z.string())
    .parse(await (await checked("/task-types", { signal })).json());
}
export async function getScheduling(
  id: string,
  signal?: AbortSignal,
): Promise<VersionedScheduling> {
  const response = await checked(`/${id}`, { signal });
  const etag = response.headers.get("ETag");
  if (!etag)
    throw new Error("Backend did not provide an ETag. Reload before editing.");
  return { scheduling: schedulingSchema.parse(await response.json()), etag };
}
export type Mutation =
  | { kind: "create"; request: SchedulingRequest }
  | { kind: "edit"; id: string; etag: string; request: SchedulingRequest }
  | { kind: "toggle"; id: string; etag: string; enabled: boolean }
  | { kind: "delete"; id: string; etag: string };
export async function mutateScheduling(action: Mutation): Promise<void> {
  const response = await checked(
    action.kind === "create" ? "" : `/${action.id}`,
    {
      method: {
        create: "POST",
        edit: "PUT",
        toggle: "PATCH",
        delete: "DELETE",
      }[action.kind],
      headers: {
        "Content-Type": "application/json",
        ...("etag" in action ? { "If-Match": action.etag } : {}),
      },
      body:
        action.kind === "delete"
          ? undefined
          : JSON.stringify(
              action.kind === "toggle"
                ? { enabled: action.enabled }
                : action.request,
            ),
    },
  );
  if (action.kind !== "delete") schedulingSchema.parse(await response.json());
}
