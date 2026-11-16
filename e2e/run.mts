import { promisify } from "node:util";
import { execFile, spawn } from "node:child_process";
import { setTimeout } from "node:timers/promises";
import { fileURLToPath } from "node:url";

const root = fileURLToPath(new URL("../", import.meta.url));
const project = "memcyco-e2e";
const compose = [
  "compose",
  "--project-name",
  project,
  "--file",
  "e2e/compose.yaml",
];
const env = {
  ...process.env,
  E2E_BASE_URL: `http://127.0.0.1:${process.env.E2E_FRONTEND_PORT ?? "15173"}`,
  E2E_API_URL: `http://127.0.0.1:${process.env.E2E_BACKEND_PORT ?? "18080"}`,
};
function run(command: string, args: string[]) {
  return new Promise<void>((resolve, reject) => {
    const child = spawn(command, args, { cwd: root, env, stdio: "inherit" });
    child.once("error", reject);
    child.once("exit", (code) =>
      code === 0
        ? resolve()
        : reject(new Error(`${command} exited with ${code}`)),
    );
  });
}
async function ready(url: string, service: string) {
  const deadline = Date.now() + 240_000;
  let iteration = 0;
  while (Date.now() < deadline) {
    if (iteration++ % 10 === 0) {
      const { stdout } = await promisify(execFile)(
        "docker",
        [...compose, "ps", "--all", "--format", "{{.State}}", service],
        { cwd: root, env },
      );
      if (stdout.includes("exited") || stdout.includes("dead"))
        throw new Error(`${service} stopped before becoming ready`);
    }
    try {
      if ((await fetch(url, { signal: AbortSignal.timeout(2000) })).ok) return;
    } catch {
      /* Startup is still in progress. */
    }
    await setTimeout(1000);
  }
  throw new Error(`Timed out waiting for ${url}`);
}
let cleanup: Promise<void> | undefined;
function stop() {
  cleanup ??= run("docker", [
    ...compose,
    "down",
    "--volumes",
    "--remove-orphans",
  ]);
  return cleanup;
}
for (const signal of ["SIGINT", "SIGTERM"]) {
  process.once(signal, () => {
    void stop().finally(() => process.exit(1));
  });
}
try {
  await run("docker", [
    ...compose,
    "up",
    "-d",
    "--wait",
    "--wait-timeout",
    "150",
  ]);
  console.log("Waiting for the isolated frontend and backend…");
  await Promise.all([
    ready(env.E2E_BASE_URL, "frontend"),
    ready(`${env.E2E_API_URL}/api/schedulings/task-types`, "backend"),
  ]);
  await run(process.execPath, [
    "node_modules/@playwright/test/cli.js",
    "test",
    "--config",
    "e2e/playwright.config.ts",
  ]);
} catch (error) {
  console.error(error);
  await run("docker", [...compose, "logs", "--tail=100"]).catch(() => {});
  process.exitCode = 1;
} finally {
  await stop();
}
