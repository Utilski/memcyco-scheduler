import { Button, Select } from "../atoms/controls";

export function Pagination({
  page,
  size,
  totalItems,
  totalPages,
  busy,
  onPage,
  onSize,
}: {
  page: number;
  size: number;
  totalItems: number;
  totalPages: number;
  busy: boolean;
  onPage: (page: number) => void;
  onSize: (size: number) => void;
}) {
  const start = Math.max(0, Math.min(page - 2, totalPages - 5));
  const pages = Array.from(
    { length: Math.min(5, totalPages) },
    (_, offset) => start + offset,
  );
  return (
    <footer className="pagination">
      <div className="pagination-size">
        <label>
          Rows per page{" "}
          <Select
            aria-label="Rows per page"
            value={size}
            onChange={(event) => onSize(Number(event.target.value))}
          >
            {[10, 20, 50, 100].map((value) => (
              <option key={value}>{value}</option>
            ))}
          </Select>
        </label>
        <span className="muted">
          {totalItems
            ? `${page * size + 1}–${Math.min((page + 1) * size, totalItems)} of ${totalItems}`
            : "0 results"}
        </span>
      </div>
      <nav aria-label="Table pagination">
        <Button
          aria-label="First page"
          disabled={page === 0 || busy}
          onClick={() => onPage(0)}
        >
          «
        </Button>
        <Button
          aria-label="Previous page"
          disabled={page === 0 || busy}
          onClick={() => onPage(page - 1)}
        >
          ‹
        </Button>
        {pages.map((value) => (
          <Button
            key={value}
            aria-label={`Page ${value + 1}`}
            aria-current={value === page ? "page" : undefined}
            variant={value === page ? "primary" : "quiet"}
            disabled={busy}
            onClick={() => onPage(value)}
          >
            {value + 1}
          </Button>
        ))}
        <Button
          aria-label="Next page"
          disabled={page >= totalPages - 1 || busy}
          onClick={() => onPage(page + 1)}
        >
          ›
        </Button>
        <Button
          aria-label="Last page"
          disabled={page >= totalPages - 1 || busy}
          onClick={() => onPage(totalPages - 1)}
        >
          »
        </Button>
      </nav>
    </footer>
  );
}
