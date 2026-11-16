import { afterEach, expect, it, vi } from "vitest";
import { ApiError, listSchedulings, getScheduling } from "./api";
import { initialQuery } from "../model/types";

afterEach(() => vi.unstubAllGlobals());
it("sends every pagination/filter parameter to the server and forwards cancellation", async () => {
  const fetch = vi.fn().mockResolvedValue(
    new Response(
      JSON.stringify({
        items: [],
        page: 2,
        size: 10,
        totalItems: 21,
        totalPages: 3,
      }),
    ),
  );
  vi.stubGlobal("fetch", fetch);
  const signal = new AbortController().signal;
  await listSchedulings(
    {
      ...initialQuery,
      page: 2,
      size: 10,
      search: "100%_literal",
      enabled: "false",
      sort: "name",
      direction: "desc",
    },
    signal,
  );
  const [url, options] = fetch.mock.calls[0];
  const params = new URL(String(url)).searchParams;
  expect(Object.fromEntries(params)).toEqual({
    page: "2",
    size: "10",
    search: "100%_literal",
    enabled: "false",
    sort: "name",
    direction: "desc",
  });
  expect(options.signal).toBe(signal);
});
it("preserves AbortError instead of converting cancellation into a network error", async () => {
  vi.stubGlobal(
    "fetch",
    vi.fn().mockRejectedValue(new DOMException("Canceled", "AbortError")),
  );
  await expect(listSchedulings(initialQuery)).rejects.toMatchObject({
    name: "AbortError",
  });
});
it("reports backend problem details with status and validates untrusted collection responses", async () => {
  vi.stubGlobal(
    "fetch",
    vi.fn().mockResolvedValue(
      new Response(JSON.stringify({ detail: "Scheduling not found" }), {
        status: 404,
      }),
    ),
  );
  await expect(getScheduling("missing")).rejects.toEqual(
    new ApiError(404, "Scheduling not found"),
  );
  vi.stubGlobal(
    "fetch",
    vi.fn().mockResolvedValue(new Response(JSON.stringify([]))),
  );
  await expect(listSchedulings(initialQuery)).rejects.toThrow();
});
