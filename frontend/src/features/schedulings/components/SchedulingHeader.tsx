import { Button } from "../../../components/atoms/controls";

export function SchedulingHeader({
  count,
  connectionError,
  onCreate,
}: {
  count?: number;
  connectionError: boolean;
  onCreate: () => void;
}) {
  return (
    <>
      <section className="page-heading">
        <div>
          <p className="eyebrow">ORCHESTRATE YOUR WORK</p>
          <h1>
            Schedulings<span className="heading-dot">.</span>
          </h1>
          <p className="page-description">
            Define the task. Set the timing. Keep everything in motion.
          </p>
        </div>
        <Button variant="primary" onClick={onCreate}>
          <span aria-hidden="true">＋</span> Create scheduling
        </Button>
      </section>
      <section className="overview-strip" aria-label="Scheduling overview">
        <div>
          <span className="overview-label">MATCHING SCHEDULES</span>
          <strong>{count ?? "—"}</strong>
        </div>
        <div>
          <span className="overview-label">SCHEDULING</span>
          <strong className="overview-text">Quartz native</strong>
        </div>
        <div>
          <span className="overview-label">COLLECTION SYNC</span>
          <strong className="overview-text">
            <span
              className={connectionError ? "status-dot bad" : "status-dot"}
            />
            {connectionError ? "Connection error" : "Every 10 seconds"}
          </strong>
        </div>
      </section>
    </>
  );
}
