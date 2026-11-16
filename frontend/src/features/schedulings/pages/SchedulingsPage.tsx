import { useState } from "react";
import { Button } from "../../../components/atoms/controls";
import { Pagination } from "../../../components/molecules/Pagination";
import { errorMessage } from "../../../lib/errors";
import { useSchedulings } from "../hooks/queries";
import { useDebouncedValue } from "../../../hooks/useDebouncedValue";
import { SchedulingDialog } from "../components/SchedulingDialog";
import { WorkspaceLayout } from "../../../components/templates/WorkspaceLayout";
import { SchedulingHeader } from "../components/SchedulingHeader";
import { SchedulingToolbar } from "../components/SchedulingToolbar";
import { SchedulingTable } from "../components/SchedulingTable";
import { initialQuery } from "../model/types";
import type { DialogState, ListQuery, SortField } from "../model/types";

export function SchedulingsPage() {
  const [query, setQuery] = useState<ListQuery>(initialQuery);
  const [search, setSearch] = useState("");
  const debouncedSearch = useDebouncedValue(search);
  const [dialog, setDialog] = useState<DialogState>({ kind: "closed" });
  const [notice, setNotice] = useState("");
  // Use the debounced search in the key directly: no stale response can overwrite another page.
  const effectiveQuery = {
    ...query,
    search: debouncedSearch,
    page: query.search !== debouncedSearch ? 0 : query.page,
  };
  if (query.search !== debouncedSearch) setQuery(effectiveQuery);
  const collection = useSchedulings(effectiveQuery);
  const data = collection.data;
  if (
    data &&
    !collection.isPlaceholderData &&
    query.page > 0 &&
    query.page >= data.totalPages
  ) {
    setQuery((previous) => ({
      ...previous,
      page: Math.max(0, data.totalPages - 1),
    }));
  }
  function changeSort(sort: SortField) {
    setQuery((previous) => ({
      ...previous,
      sort,
      direction:
        previous.sort === sort && previous.direction === "asc" ? "desc" : "asc",
      page: 0,
    }));
  }
  return (
    <WorkspaceLayout>
      <SchedulingHeader
        count={data?.totalItems}
        connectionError={collection.isError}
        onCreate={() => {
          setNotice("");
          setDialog({ kind: "create" });
        }}
      />
      {notice && (
        <div role="status" className="success">
          {notice}
          <Button
            variant="quiet"
            onClick={() => setNotice("")}
            aria-label="Dismiss notification"
          >
            ✕
          </Button>
        </div>
      )}
      <section className="table-panel" aria-label="Scheduling management">
        <div className="panel-heading">
          <h2>
            All schedulings{" "}
            <span className="result-count">{data?.totalItems ?? 0}</span>
          </h2>
          <span className="muted small">
            {collection.isFetching ? "Updating…" : "Server-side results"}
          </span>
        </div>
        <SchedulingToolbar
          search={search}
          query={query}
          loading={collection.isFetching}
          onSearch={(value) => {
            setSearch(value);
            setQuery((previous) => ({ ...previous, page: 0 }));
          }}
          onEnabled={(enabled) =>
            setQuery((previous) => ({ ...previous, enabled, page: 0 }))
          }
          onSort={changeSort}
          onReverse={() =>
            setQuery((previous) => ({
              ...previous,
              direction: previous.direction === "asc" ? "desc" : "asc",
              page: 0,
            }))
          }
          onRefresh={() => void collection.refetch()}
        />
        {collection.isError && (
          <div role="alert" className="error panel-feedback">
            {errorMessage(collection.error)}{" "}
            <Button onClick={() => void collection.refetch()}>Retry</Button>
          </div>
        )}
        {collection.isPending ? (
          <div role="status" className="empty-state">
            <span className="spinner" />
            Loading schedulings…
          </div>
        ) : data?.items.length ? (
          <SchedulingTable
            items={data.items}
            query={effectiveQuery}
            updating={collection.isFetching}
            onSort={changeSort}
            onAction={(kind, id) => {
              setNotice("");
              setDialog({ kind, id });
            }}
          />
        ) : (
          !collection.isError && (
            <div className="empty-state">
              <div className="empty-icon">▦</div>
              <h3>
                {search || query.enabled
                  ? "No matching schedulings"
                  : "Your workspace is ready"}
              </h3>
              <p>
                {search || query.enabled
                  ? "Try a different search or state filter."
                  : "Create your first scheduling to put a task in motion."}
              </p>
              {!search && !query.enabled && (
                <Button
                  variant="primary"
                  onClick={() => setDialog({ kind: "create" })}
                >
                  Create scheduling
                </Button>
              )}
            </div>
          )
        )}
        {data && (
          <Pagination
            page={query.page}
            size={query.size}
            totalItems={data.totalItems}
            totalPages={data.totalPages}
            busy={collection.isPlaceholderData}
            onPage={(page) => setQuery((previous) => ({ ...previous, page }))}
            onSize={(size) =>
              setQuery((previous) => ({ ...previous, size, page: 0 }))
            }
          />
        )}
      </section>
      {dialog.kind !== "closed" && (
        <SchedulingDialog
          key={dialog.kind + ("id" in dialog ? dialog.id : "")}
          state={dialog}
          onClose={() => setDialog({ kind: "closed" })}
          onSuccess={setNotice}
        />
      )}
    </WorkspaceLayout>
  );
}
