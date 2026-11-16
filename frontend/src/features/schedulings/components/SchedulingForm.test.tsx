import type { ReactNode } from "react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, expect, it, vi } from "vitest";
import * as api from "../api/api";
import { SchedulingForm } from "./SchedulingForm";
import { SchedulingDialog } from "./SchedulingDialog";
import { Pagination } from "../../../components/molecules/Pagination";
import { schedulingFixture as fixture } from "../../../test/schedulingFixture";

function wrapper(children: ReactNode) {
  const client = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  });
  return render(
    <QueryClientProvider client={client}>{children}</QueryClientProvider>,
  );
}
afterEach(() => vi.restoreAllMocks());
it("loads task select options from backend, including unsupported future values", async () => {
  vi.spyOn(api, "loadTaskTypes").mockResolvedValue([
    "LOG",
    "DB_QUERY",
    "FUTURE_TASK",
  ]);
  const submit = vi.fn();
  wrapper(<SchedulingForm busy={false} onCancel={vi.fn()} onSubmit={submit} />);
  const user = userEvent.setup();
  await screen.findByRole("option", { name: "Future Task" });
  expect(
    screen.queryByRole("option", { name: "Dummy Email" }),
  ).not.toBeInTheDocument();
  await user.selectOptions(screen.getByLabelText("Task type"), "FUTURE_TASK");
  expect(
    screen.getByRole("button", { name: "Create scheduling" }),
  ).toBeDisabled();
  await user.selectOptions(screen.getByLabelText("Task type"), "DB_QUERY");
  expect(screen.getByLabelText("Query text")).toBeInTheDocument();
  expect(submit).not.toHaveBeenCalled();
});
it("keeps draft after server conflict and reloads only on explicit action", async () => {
  vi.spyOn(api, "loadTaskTypes").mockResolvedValue(["LOG"]);
  const get = vi
    .spyOn(api, "getScheduling")
    .mockResolvedValue({ scheduling: fixture, etag: '"0"' });
  const mutation = vi
    .spyOn(api, "mutateScheduling")
    .mockRejectedValue(new api.ApiError(409, "Changed by another request"));
  wrapper(
    <SchedulingDialog
      state={{ kind: "edit", id: fixture.id }}
      onClose={vi.fn()}
      onSuccess={vi.fn()}
    />,
  );
  const user = userEvent.setup();
  const name = await screen.findByLabelText("Name");
  await screen.findByRole("option", { name: "Log" });
  await user.clear(name);
  await user.type(name, "My draft");
  await user.click(screen.getByRole("button", { name: "Save changes" }));
  await screen.findByText("Changed by another request");
  expect(screen.getByLabelText("Name")).toHaveValue("My draft");
  expect(mutation).toHaveBeenCalledWith(
    expect.objectContaining({ etag: '"0"' }),
    expect.anything(),
  );
  get.mockResolvedValue({
    scheduling: { ...fixture, name: "Latest" },
    etag: '"1"',
  });
  await user.click(
    screen.getByRole("button", { name: "Reload latest version" }),
  );
  await waitFor(() =>
    expect(screen.getByLabelText("Name")).toHaveValue("Latest"),
  );
});
it("shows task-option errors and blocks submission until retry succeeds", async () => {
  const load = vi
    .spyOn(api, "loadTaskTypes")
    .mockRejectedValue(new Error("Offline"));
  wrapper(
    <SchedulingForm busy={false} onCancel={vi.fn()} onSubmit={vi.fn()} />,
  );
  await screen.findByText(
    "Could not load task types.",
    { exact: false },
    { timeout: 3000 },
  );
  expect(
    screen.getByRole("button", { name: "Create scheduling" }),
  ).toBeDisabled();
  load.mockResolvedValue(["LOG"]);
  await userEvent
    .setup()
    .click(screen.getByRole("button", { name: "Retry task types" }));
  await screen.findByRole("option", { name: "Log" });
});
it("pagination reports server totals and emits page and size changes", async () => {
  const page = vi.fn(),
    size = vi.fn();
  render(
    <Pagination
      page={0}
      size={20}
      totalItems={42}
      totalPages={3}
      busy={false}
      onPage={page}
      onSize={size}
    />,
  );
  const user = userEvent.setup();
  expect(screen.getByText("1–20 of 42")).toBeInTheDocument();
  await user.click(screen.getByRole("button", { name: "Next page" }));
  expect(page).toHaveBeenCalledWith(1);
  await user.selectOptions(screen.getByLabelText("Rows per page"), "50");
  expect(size).toHaveBeenCalledWith(50);
});

it("closes and refreshes when a mutation discovers a deleted record", async () => {
  vi.spyOn(api, "getScheduling").mockResolvedValue({
    scheduling: fixture,
    etag: '"0"',
  });
  vi.spyOn(api, "mutateScheduling").mockRejectedValue(
    new api.ApiError(404, "Scheduling not found"),
  );
  const close = vi.fn(),
    notify = vi.fn();
  wrapper(
    <SchedulingDialog
      state={{ kind: "delete", id: fixture.id }}
      onClose={close}
      onSuccess={notify}
    />,
  );
  await userEvent
    .setup()
    .click(await screen.findByRole("button", { name: "Delete scheduling" }));
  await waitFor(() => expect(close).toHaveBeenCalledOnce());
  expect(notify).toHaveBeenCalledWith("This scheduling no longer exists.");
});
