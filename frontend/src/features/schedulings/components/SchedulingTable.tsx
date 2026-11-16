import { Badge, Button } from "../../../components/atoms/controls";
import {
  formatInstant,
  formatLocal,
  humanize,
  jsonSummary,
  scheduleSummary,
} from "../utils/format";
import type { Action, ListQuery, Scheduling, SortField } from "../model/types";

export function SchedulingTable({
  items,
  query,
  onSort,
  onAction,
  updating,
}: {
  items: Scheduling[];
  query: ListQuery;
  onSort: (field: SortField) => void;
  onAction: (action: Action, id: string) => void;
  updating: boolean;
}) {
  function heading(label: string, field: SortField) {
    return (
      <th
        aria-sort={
          query.sort === field
            ? query.direction === "asc"
              ? "ascending"
              : "descending"
            : "none"
        }
      >
        <button className="sort-button" onClick={() => onSort(field)}>
          {label}
          <span aria-hidden="true">
            {query.sort === field
              ? query.direction === "asc"
                ? " ↑"
                : " ↓"
              : " ↕"}
          </span>
        </button>
      </th>
    );
  }
  return (
    <div className="table-scroll" aria-busy={updating}>
      <table>
        <caption className="sr-only">
          Existing schedulings and available actions
        </caption>
        <thead>
          <tr>
            {heading("Scheduling", "name")}
            {heading("Task", "taskType")}
            {heading("Schedule", "scheduleType")}
            {heading("Time bounds", "startsAt")}
            {heading("Next run", "nextRunAt")}
            <th>Dispatched</th>
            {heading("State", "enabled")}
            {heading("Updated", "updatedAt")}
            <th className="actions-heading">Actions</th>
          </tr>
        </thead>
        <tbody>
          {items.map((item) => (
            <tr key={item.id}>
              <td>
                <strong>{item.name}</strong>
                <p
                  className="cell-description"
                  title={item.description ?? undefined}
                >
                  {item.description || "No description"}
                </p>
                <span className="cell-meta">
                  Created {formatInstant(item.createdAt)}
                </span>
              </td>
              <td>
                <Badge>{humanize(item.taskType)}</Badge>
                <p
                  className="cell-description mono"
                  title={jsonSummary(item.payload)}
                >
                  {jsonSummary(item.payload)}
                </p>
              </td>
              <td>
                <span className={item.scheduleType === "CRON" ? "mono" : ""}>
                  {scheduleSummary(item)}
                </span>
                <p className="cell-meta">{item.timeZone}</p>
              </td>
              <td>
                <span className="date-value">{formatLocal(item.startsAt)}</span>
                <p className="cell-meta">
                  {item.endsAt
                    ? `Until ${formatInstant(item.endsAt)} UTC`
                    : "No end date"}
                </p>
              </td>
              <td>
                <span className="date-value">
                  {formatInstant(item.nextRunAt, item.timeZone)}
                </span>
                <p className="cell-meta">
                  {item.nextRunAt ? item.timeZone : "No eligible run scheduled"}
                </p>
              </td>
              <td>
                <span className="count-value">{item.dispatchedCount}</span>
                <span className="muted"> / {item.maxOccurrences ?? "∞"}</span>
              </td>
              <td>
                <Badge tone={item.enabled ? "positive" : "muted"}>
                  {item.enabled ? "Enabled" : "Disabled"}
                </Badge>
              </td>
              <td>
                <span className="date-value">
                  {formatInstant(item.updatedAt)}
                </span>
                <p className="cell-meta">UTC</p>
              </td>
              <td>
                <div className="row-actions">
                  <Button
                    variant="quiet"
                    onClick={() => onAction("view", item.id)}
                    aria-label={`View ${item.name}`}
                  >
                    View
                  </Button>
                  <Button
                    variant="quiet"
                    onClick={() => onAction("edit", item.id)}
                    aria-label={`Edit ${item.name}`}
                  >
                    Edit
                  </Button>
                  <Button
                    variant="quiet"
                    onClick={() => onAction("toggle", item.id)}
                    aria-label={`${item.enabled ? "Disable" : "Enable"} ${item.name}`}
                  >
                    {item.enabled ? "Disable" : "Enable"}
                  </Button>
                  <Button
                    variant="quiet"
                    className="delete-action"
                    onClick={() => onAction("delete", item.id)}
                    aria-label={`Delete ${item.name}`}
                  >
                    Delete
                  </Button>
                </div>
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
