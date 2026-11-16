import { Badge } from "../../../components/atoms/controls";
import {
  formatInstant,
  formatLocal,
  humanize,
  scheduleSummary,
} from "../utils/format";
import type { Scheduling } from "../model/types";

export function SchedulingDetails({ scheduling }: { scheduling: Scheduling }) {
  return (
    <div className="details">
      <Badge tone={scheduling.enabled ? "positive" : "muted"}>
        {scheduling.enabled ? "Enabled" : "Disabled"}
      </Badge>
      <dl>
        {Object.entries({
          Name: scheduling.name,
          Description: scheduling.description ?? "—",
          ID: scheduling.id,
          Task: humanize(scheduling.taskType),
          Schedule: scheduleSummary(scheduling),
          Timezone: scheduling.timeZone,
          "Local start": formatLocal(scheduling.startsAt),
          "End (UTC)": scheduling.endsAt
            ? formatInstant(scheduling.endsAt)
            : "No end",
          "Next run (schedule timezone)": formatInstant(
            scheduling.nextRunAt,
            scheduling.timeZone,
          ),
          "Dispatched / limit": `${scheduling.dispatchedCount} / ${scheduling.maxOccurrences ?? "Unlimited"}`,
          "Created (UTC)": formatInstant(scheduling.createdAt),
          "Updated (UTC)": formatInstant(scheduling.updatedAt),
          Version: scheduling.version,
        }).map(([label, value]) => (
          <div key={label}>
            <dt>{label}</dt>
            <dd>{value}</dd>
          </div>
        ))}
      </dl>
      <h3>Task payload</h3>
      <pre>{JSON.stringify(scheduling.payload, null, 2)}</pre>
      <h3>Schedule configuration</h3>
      <pre>{JSON.stringify(scheduling.scheduleConfig, null, 2)}</pre>
    </div>
  );
}
