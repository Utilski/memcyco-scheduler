import { execFileSync } from "node:child_process";
import {
  pageSchema,
  schedulingSchema,
} from "../../frontend/src/features/schedulings/model/types";
import { expect, test } from "@playwright/test";
import type { APIRequestContext } from "@playwright/test";

const api = process.env.E2E_API_URL ?? "http://127.0.0.1:18080";
const prefix = `E2E-${Date.now()}`;
const createdIds = new Set<string>();
async function create(
  request: APIRequestContext,
  name: string,
  enabled = false,
) {
  const response = await request.post(`${api}/api/schedulings`, {
    data: {
      name,
      description: "Browser integration fixture",
      taskType: "LOG",
      payload: { message: "E2E" },
      scheduleType: "ONE_TIME",
      timeZone: "UTC",
      startsAt: "2030-01-01T09:00:00",
      scheduleConfig: {},
      enabled,
    },
  });
  expect(response.ok()).toBeTruthy();
  const data = schedulingSchema.parse(await response.json());
  createdIds.add(data.id);
  return data;
}
test.afterAll(async ({ request }) => {
  for (const id of createdIds)
    await request.delete(`${api}/api/schedulings/${id}`);
});

test("guided CRUD uses backend enum options, dialogs, concurrency checks, and keyboard focus", async ({
  page,
  request,
}) => {
  await page.goto("/");
  const createButton = page
    .getByRole("button", { name: "Create scheduling" })
    .first();
  await createButton.click();
  const dialog = page.getByRole("dialog");
  const types: unknown = await (
    await request.get(`${api}/api/schedulings/task-types`)
  ).json();
  expect(Array.isArray(types)).toBeTruthy();
  await expect(dialog.getByLabel("Task type")).toBeEnabled();
  const values = await dialog
    .getByLabel("Task type")
    .locator("option")
    .evaluateAll((options) =>
      options.map((option) => option.getAttribute("value")).filter(Boolean),
    );
  expect(values).toEqual(types);
  const name = `${prefix}-CRUD`;
  await dialog.getByLabel("Name", { exact: true }).fill(name);
  await dialog.getByLabel("Task type").selectOption("LOG");
  await dialog.getByLabel("Log message").fill("Created from the browser");
  await dialog.getByLabel("Enabled", { exact: true }).uncheck();
  await dialog.getByRole("button", { name: "Create scheduling" }).click();
  await expect(dialog).not.toBeVisible();
  await page.getByLabel("Search schedulings").fill(name);
  const row = page
    .getByRole("row")
    .filter({ has: page.getByText(name, { exact: true }) });
  await expect(row).toHaveCount(1);
  const result = pageSchema.parse(
    await (
      await request.get(
        `${api}/api/schedulings?search=${encodeURIComponent(name)}`,
      )
    ).json(),
  );
  const id = result.items[0].id;
  createdIds.add(id);
  await row.getByRole("button", { name: `View ${name}`, exact: true }).click();
  await expect(dialog).toContainText("Created from the browser");
  await page.keyboard.press("Escape");
  await expect(dialog).not.toBeVisible();
  await expect(
    row.getByRole("button", { name: `View ${name}`, exact: true }),
  ).toBeFocused();
  await row.getByRole("button", { name: `Edit ${name}`, exact: true }).click();
  await dialog.getByLabel("Name", { exact: true }).fill(`${name}-edited`);
  // Real concurrent backend client, not request interception.
  const current = await request.get(`${api}/api/schedulings/${id}`);
  expect(
    (
      await request.patch(`${api}/api/schedulings/${id}`, {
        headers: { "If-Match": current.headers().etag },
        data: { description: "Concurrent change" },
      })
    ).ok(),
  ).toBeTruthy();
  await dialog.getByRole("button", { name: "Save changes" }).click();
  await expect(dialog.getByRole("alert")).toContainText("changed");
  await expect(dialog.getByLabel("Name", { exact: true })).toHaveValue(
    `${name}-edited`,
  );
  await dialog.getByRole("button", { name: "Reload latest version" }).click();
  await expect(dialog.getByLabel("Name", { exact: true })).toHaveValue(name);
  await dialog.getByLabel("Name", { exact: true }).fill(`${name}-edited`);
  await dialog.getByRole("button", { name: "Save changes" }).click();
  await expect(dialog).not.toBeVisible();
  const edited = `${name}-edited`;
  const editedRow = page
    .getByRole("row")
    .filter({ has: page.getByText(edited, { exact: true }) });
  await editedRow
    .getByRole("button", { name: `Enable ${edited}`, exact: true })
    .click();
  await dialog.getByRole("button", { name: "Enable scheduling" }).click();
  await expect(editedRow.getByText("Enabled", { exact: true })).toBeVisible();
  await editedRow
    .getByRole("button", { name: `Disable ${edited}`, exact: true })
    .click();
  await dialog.getByRole("button", { name: "Disable scheduling" }).click();
  await expect(editedRow.getByText("Disabled", { exact: true })).toBeVisible();
  await editedRow
    .getByRole("button", { name: `Delete ${edited}`, exact: true })
    .click();
  await dialog.getByRole("button", { name: "Delete scheduling" }).click();
  await expect(editedRow).toHaveCount(0);
});

test("server pagination searches and sorts the entire collection and clamps deletion from last page", async ({
  page,
  request,
}) => {
  const group = `${prefix}-pages`;
  for (let index = 0; index < 21; index++)
    await create(
      request,
      `${group}-${String(index).padStart(2, "0")}`,
      index % 2 === 0,
    );
  await page.goto("/");
  const appliedSearch = page.waitForResponse((response) => {
    const url = new URL(response.url());
    return (
      url.pathname === "/api/schedulings" &&
      url.searchParams.get("search") === group &&
      url.searchParams.get("sort") === "name" &&
      response.ok()
    );
  });
  await page.getByLabel("Search schedulings").fill(group);
  await page.getByLabel("Sort schedulings").selectOption("name");
  await appliedSearch;
  await expect(page.getByText("1–20 of 21")).toBeVisible();
  await page.getByRole("button", { name: "Next page", exact: true }).click();
  await expect(page.getByText("21–21 of 21")).toBeVisible();
  await page
    .getByRole("button", { name: `Delete ${group}-20`, exact: true })
    .click();
  await page
    .getByRole("dialog")
    .getByRole("button", { name: "Delete scheduling" })
    .click();
  await expect(page.getByText("1–20 of 20")).toBeVisible();
  await page.getByLabel("Rows per page").selectOption("10");
  await expect(page.getByText("1–10 of 20")).toBeVisible();
  await page.getByRole("button", { name: "Reverse sort direction" }).click();
  await expect(page.getByRole("row").nth(1)).toContainText(`${group}-19`);
  await page.getByLabel("Filter by enabled state").selectOption("true");
  await expect(page.getByText("1–10 of 10")).toBeVisible();
  await page.getByLabel("Search schedulings").fill(`${group}-18`);
  await expect(page.getByText("1–1 of 1")).toBeVisible();
  await expect(page.getByRole("row").nth(1)).toContainText(`${group}-18`);
});

test("actual Quartz and RabbitMQ execution advances progress through the browser", async ({
  page,
  request,
}) => {
  const name = `${prefix}-execution`;
  await page.goto("/");
  await page.getByRole("button", { name: "Create scheduling" }).first().click();
  const dialog = page.getByRole("dialog");
  await dialog.getByLabel("Name", { exact: true }).fill(name);
  await dialog.getByLabel("Task type").selectOption("LOG");
  await dialog.getByLabel("Log message").fill("Full project execution test");
  await dialog
    .getByLabel("Start time (in selected timezone)")
    .fill(new Date(Date.now() + 4000).toISOString().slice(0, -1));
  await dialog.getByRole("button", { name: "Create scheduling" }).click();
  await expect(dialog).not.toBeVisible();
  await page.getByLabel("Search schedulings").fill(name);
  const result = pageSchema.parse(
    await (
      await request.get(
        `${api}/api/schedulings?search=${encodeURIComponent(name)}`,
      )
    ).json(),
  );
  const schedulingId = result.items[0].id;
  createdIds.add(schedulingId);
  const row = page
    .getByRole("row")
    .filter({ has: page.getByText(name, { exact: true }) });
  await expect(async () => {
    await page.getByRole("button", { name: "Refresh schedulings" }).click();
    await expect(row.locator(".count-value")).toHaveText("1");
    await expect(row).toContainText("No next run");
    const status = execFileSync(
      "docker",
      [
        "compose",
        "--project-name",
        "memcyco-e2e",
        "--file",
        "e2e/compose.yaml",
        "exec",
        "-T",
        "mysql",
        "mysql",
        "-utestuser",
        "-ptestpass",
        "scheduler_e2e",
        "-N",
        "-e",
        `SELECT status FROM task_executions WHERE scheduling_id = '${schedulingId}'`,
      ],
      { encoding: "utf8" },
    );
    expect(status.trim()).toBe("SUCCEEDED");
  }).toPass({ timeout: 25_000 });
});

test("narrow viewport keeps dialogs and table controls usable", async ({
  page,
  request,
}) => {
  await create(request, `${prefix}-mobile`);
  await page.setViewportSize({ width: 390, height: 844 });
  await page.goto("/");
  await expect(
    page.getByRole("heading", { name: "Schedulings." }),
  ).toBeVisible();
  await page.getByRole("button", { name: "Create scheduling" }).first().click();
  await expect(page.getByRole("dialog")).toBeVisible();
  expect(
    await page
      .getByRole("dialog")
      .evaluate((element) => element.getBoundingClientRect().width),
  ).toBeLessThanOrEqual(390);
  await page.keyboard.press("Escape");
  const overflow = await page.evaluate(
    () => document.documentElement.scrollWidth > window.innerWidth,
  );
  expect(overflow).toBe(false);
});
