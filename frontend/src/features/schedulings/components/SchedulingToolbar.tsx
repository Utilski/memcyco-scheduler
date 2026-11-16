import { Button, Input, Select } from "../../../components/atoms/controls";
import { sortSchema } from "../model/types";
import type { ListQuery, SortField } from "../model/types";

const sortLabels: Record<SortField, string> = {
  createdAt: "Created",
  name: "Name",
  taskType: "Task",
  scheduleType: "Schedule",
  enabled: "State",
  startsAt: "Start",
  nextRunAt: "Next run",
  updatedAt: "Updated",
};
export function SchedulingToolbar({
  search,
  query,
  loading,
  onSearch,
  onEnabled,
  onSort,
  onReverse,
  onRefresh,
}: {
  search: string;
  query: ListQuery;
  loading: boolean;
  onSearch: (value: string) => void;
  onEnabled: (value: ListQuery["enabled"]) => void;
  onSort: (sort: SortField) => void;
  onReverse: () => void;
  onRefresh: () => void;
}) {
  return (
    <div className="table-toolbar">
      <div className="search-control">
        <span aria-hidden="true">⌕</span>
        <Input
          aria-label="Search schedulings"
          placeholder="Search names or descriptions…"
          value={search}
          onChange={(event) => onSearch(event.target.value)}
        />
      </div>
      <label className="toolbar-filter">
        State{" "}
        <Select
          aria-label="Filter by enabled state"
          value={query.enabled}
          onChange={(event) => {
            const value = event.target.value;
            if (value === "" || value === "true" || value === "false")
              onEnabled(value);
          }}
        >
          <option value="">All states</option>
          <option value="true">Enabled</option>
          <option value="false">Disabled</option>
        </Select>
      </label>
      <label className="toolbar-filter">
        Sort{" "}
        <Select
          aria-label="Sort schedulings"
          value={query.sort}
          onChange={(event) => onSort(sortSchema.parse(event.target.value))}
        >
          {sortSchema.options.map((value) => (
            <option key={value} value={value}>
              {sortLabels[value]}
            </option>
          ))}
        </Select>
      </label>
      <Button onClick={onReverse} aria-label="Reverse sort direction">
        {query.direction === "asc" ? "↑ Asc" : "↓ Desc"}
      </Button>
      <Button
        variant="quiet"
        onClick={onRefresh}
        aria-label="Refresh schedulings"
        disabled={loading}
      >
        ↻ Refresh
      </Button>
    </div>
  );
}
